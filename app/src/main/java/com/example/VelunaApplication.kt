package com.example

import android.app.Application
import com.example.data.local.UserPrefsRepository
import com.example.data.local.VelunaDatabase
import com.example.data.network.InnerTubeClient
import com.example.data.network.LyricsRepository
import com.example.data.network.ScrobbleClient
import com.example.data.repository.VelunaRepository
import com.example.playback.PlayerController

class VelunaAppContainer(application: Application) {
    val database: VelunaDatabase = VelunaDatabase.getInstance(application)
    val dao = database.velunaDao()
    val prefsRepository: UserPrefsRepository = UserPrefsRepository(application)
    val innerTubeClient: InnerTubeClient = InnerTubeClient()
    val lyricsRepository: LyricsRepository = LyricsRepository(dao, innerTubeClient)
    val scrobbleClient: ScrobbleClient = ScrobbleClient(innerTubeClient.httpClient)
    val repository: VelunaRepository = VelunaRepository(
        context = application,
        dao = dao,
        prefsRepo = prefsRepository,
        innerTube = innerTubeClient
    )
    val playerController: PlayerController = PlayerController(
        context = application,
        dao = dao,
        repository = repository,
        prefsRepo = prefsRepository,
        innerTube = innerTubeClient,
        scrobbleClient = scrobbleClient
    )
}

class VelunaApplication : Application() {
    lateinit var container: VelunaAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = VelunaAppContainer(this)
    }
}
