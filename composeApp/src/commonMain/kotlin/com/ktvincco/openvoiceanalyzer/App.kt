package com.ktvincco.openvoiceanalyzer

import androidx.compose.runtime.Composable
import com.ktvincco.openvoiceanalyzer.data.AudioPlayer
import com.ktvincco.openvoiceanalyzer.data.AudioRecorder
import com.ktvincco.openvoiceanalyzer.data.Database
import com.ktvincco.openvoiceanalyzer.data.EnvironmentConnector
import com.ktvincco.openvoiceanalyzer.data.Logger
import com.ktvincco.openvoiceanalyzer.data.PermissionController
import com.ktvincco.openvoiceanalyzer.data.SoundFile
import com.ktvincco.openvoiceanalyzer.domain.Main
import com.ktvincco.openvoiceanalyzer.presentation.ModelData
import com.ktvincco.openvoiceanalyzer.presentation.UiEventHandler
import com.ktvincco.openvoiceanalyzer.ui.UserInterface
import org.jetbrains.compose.ui.tooling.preview.Preview


expect fun epochMillis(): Long


@Composable
@Preview
fun App(
    logger: Logger,
    permissionController: PermissionController,
    audioRecorder: AudioRecorder,
    database: Database,
    soundFile: SoundFile,
    audioPlayer: AudioPlayer,
    environmentConnector: EnvironmentConnector
) {

    // Create components
    val modelData = ModelData()
    val uiEventHandler = UiEventHandler()
    val domainMain = Main(modelData, uiEventHandler, logger, permissionController,
        audioRecorder, database, soundFile, audioPlayer, environmentConnector)
    val userInterface = UserInterface(modelData, uiEventHandler)

    // Run actions
    domainMain.setup()
    userInterface.draw()
}