package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

object GoogleAuthHelper {
    private const val TAG = "Auth"

    tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun webClientId(context: Context): String {
        val id = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull().orEmpty()
        if (id.isBlank()) {
            Log.e(TAG, "default_web_client_id string resource is missing; check google-services.json")
        }
        return id
    }

    /**
     * Silent auto sign-in at app startup for users who already authorized this app on this device.
     * Uses a single GetGoogleIdOption inside GetCredentialRequest.
     */
    suspend fun attemptAutoSignIn(context: Context): Result<FirebaseUser?> {
        Firebase.auth.currentUser?.let { existing ->
            return Result.success(existing)
        }
        val activity = context.findActivity() ?: return Result.success(null)
        val clientId = webClientId(context)
        if (clientId.isBlank()) {
            return Result.failure(IllegalStateException("Google Sign-In client configuration is missing."))
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
                Result.success(authResult.user)
            } else {
                Result.success(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent auto sign-in cancelled", e)
            Result.success(null)
        } catch (_: NoCredentialException) {
            Result.success(null)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Silent auto sign-in unavailable: ${e.message}")
            Result.success(null)
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "Failed to parse Google ID token on auto sign-in", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected auto sign-in error", e)
            Result.failure(e)
        }
    }

    /**
     * Interactive Google Sign-In triggered by user tap.
     * Uses a single GetSignInWithGoogleOption inside GetCredentialRequest and an Activity context.
     */
    suspend fun signInWithGoogle(context: Context): GoogleSignInOutcome {
        val activity = context.findActivity()
            ?: return GoogleSignInOutcome.Error("Unable to open Google Sign-In from this screen.")
        val clientId = webClientId(context)
        if (clientId.isBlank()) {
            return GoogleSignInOutcome.Error("Google Sign-In is not configured for this build.")
        }

        val signInOption = GetSignInWithGoogleOption.Builder(clientId)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val credentialManager = CredentialManager.create(activity)
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                return GoogleSignInOutcome.Error("Unsupported credential returned by Google.")
            }

            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
            val user = authResult.user
            if (user != null) {
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Error("Google Sign-In did not return a user account.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google sign-in cancelled by user", e)
            GoogleSignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google accounts found on device", e)
            GoogleSignInOutcome.Error("No Google account found on this device. Add one in Android Settings first.")
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Invalid Google ID token response", e)
            GoogleSignInOutcome.Error("Failed to verify Google ID token. Please try again.")
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google credential error: ${e.message}", e)
            GoogleSignInOutcome.Error(e.errorMessage?.toString() ?: "Google Sign-In failed. Please try again.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google authentication failed", e)
            GoogleSignInOutcome.Error(e.message ?: "Google Sign-In failed.")
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }.onFailure { err ->
                Log.w(TAG, "clearCredentialState skipped: ${err.message}")
            }
            Firebase.auth.signOut()
        }
    }
}
