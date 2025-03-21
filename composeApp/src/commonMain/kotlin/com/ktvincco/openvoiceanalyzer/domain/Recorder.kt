package com.ktvincco.openvoiceanalyzer.domain

import com.ktvincco.openvoiceanalyzer.Settings
import com.ktvincco.openvoiceanalyzer.data.AudioPlayer
import com.ktvincco.openvoiceanalyzer.data.AudioRecorder
import com.ktvincco.openvoiceanalyzer.data.Database
import com.ktvincco.openvoiceanalyzer.data.Logger
import com.ktvincco.openvoiceanalyzer.data.PermissionController
import com.ktvincco.openvoiceanalyzer.data.SoundFile
import com.ktvincco.openvoiceanalyzer.presentation.ModelData
import com.ktvincco.openvoiceanalyzer.presentation.UiEventHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class Recorder (
    private val modelData: ModelData,
    private val uiEventHandler: UiEventHandler,
    private val logger: Logger,
    private val permissionController: PermissionController,
    private val audioRecorder: AudioRecorder,
    private val database: Database,
    private val soundFile: SoundFile,
    private val audioPlayer: AudioPlayer,
) {


    companion object {
        const val LOG_TAG = "Recorder"
    }


    // Settings
    private val sampleLength = Settings.getProcessingSampleLength()


    // Components
    private val audioProcessor = AudioProcessor(
        modelData, uiEventHandler, logger, permissionController,
        audioRecorder, database, soundFile, audioPlayer)


    // State
    private var isRecordingNow = false
    private var isPlayingNow = false
    private var pointerPosition = 0F
    private var playbackStartPosition = 0


    // Sound data
    private var rawData: FloatArray = floatArrayOf(0F)
    private var processedLength: Int = 0


    // Variables
    private var soundFilesList: List<String> = listOf()


    fun setup() {

        // Assign callbacks

        // Audio recorder callback
        audioRecorder.setDataCallback { newDataRaw: ShortArray ->

            val msv = Short.MAX_VALUE.toFloat()
            val nd: MutableList<Float> = mutableListOf()
            for (i in newDataRaw) { nd.add(i.toFloat() / msv) }

            rawData += nd.toFloatArray()

            processRawData()
        }

        // Audio player callback
        audioPlayer.setPositionCallback { isPlaying, position ->
            if(isPlaying) {
                pointerPosition =
                    (playbackStartPosition + position).toFloat() / (rawData.size).toFloat()
                audioProcessor.rewindDisplaysTo(pointerPosition)
            } else {
                isPlayingNow = false
                modelData.setPlaybackState(false)
            }
            updateUi()
        }

        // Rewind callback
        uiEventHandler.assignRewindCallback { newPointerPosition ->
            rewindTo(newPointerPosition)
        }

        // Rename callback
        uiEventHandler.assignRenameRecordingFileCallback { fileName, newNameInput ->
            renameRecordingFile(fileName, newNameInput)
        }

        // Delete callback
        uiEventHandler.assignDeleteRecordingFileCallback { fileName ->
            deleteRecordingFile(fileName)
        }

        // Buttons callbacks
        uiEventHandler.assignRecordButtonCallback {
            if (isRecordingNow) {
                stop()
            } else {
                start()
            }
        }
        uiEventHandler.assignPlayButtonCallback {
            if (isPlayingNow) {
                stopPlaying()
            } else {
                play()
            }
        }
        uiEventHandler.assignResetButtonCallback {
            reset()
        }
        uiEventHandler.assignSaveButtonCallback {
            saveData()
        }
        uiEventHandler.assignLoadRecordingButtonCallback { fileName ->
            loadFromFile(fileName)
        }

        // Configure

        // Update
        updateSoundFilesList()
        updateUi()
    }


    private fun processRawData() {
        // Process data
        while (rawData.size - processedLength > sampleLength) {
            audioProcessor.processData(rawData.copyOfRange(processedLength,
                processedLength + sampleLength), isRecordingNow)
            processedLength += sampleLength
        }

        // Update data duration
        val processedDataDurationSec = (processedLength / sampleLength) *
                Settings.getProcessingSampleDurationSec()
        modelData.setDataDurationSec(processedDataDurationSec)
    }


    private fun resetData() {
        audioRecorder.stopRecording()
        isRecordingNow = false
        modelData.setRecordingState(false)
        audioPlayer.stop()
        audioProcessor.reset()
        rawData = floatArrayOf(0F)
        processedLength = 0
        modelData.setDataDurationSec(1F)
        pointerPosition = 0F
        updateUi()
    }


    private fun reset() {
        resetData()
        modelData.setRecordingControlLayoutAsReadyToRecording()
    }


    private fun start() {
        resetData()
        isRecordingNow = true
        modelData.setRecordingState(true)
        modelData.setRecordingControlLayoutAsRecording()
        audioRecorder.startRecording()
        updateUi()
    }


    private fun stop() {
        audioRecorder.stopRecording()
        isRecordingNow = false
        modelData.setRecordingState(false)

        // Check if data is too short (< 2s) -> reset
        if (rawData.size < 44100 * 2) {

            // Reset all data
            reset()
        } else {

            // Update UI
            modelData.setRecordingControlLayoutAsDeleteSaveOrPlay()

            // Process all late data
            processRawData()
        }
    }


    private fun saveData() {
        val fileName = "/REC" + database.getYYYYMMDDHHMMSSString() + ".wav"
        val filePath = database.getSoundFileDirectoryPath() + fileName
        soundFile.writeSoundToFile(filePath, rawData,44100)
        updateSoundFilesList()
        modelData.setRecordingControlLayoutAsPlayer()
    }


    // Position as float from 0 (start) to 1 (end)
    private fun rewindTo(position: Float) {

        // Check state
        if (isRecordingNow) { return }

        // Update cursor
        pointerPosition = position.coerceIn(0F, 1F)

        // Update displays
        audioProcessor.rewindDisplaysTo(pointerPosition)

        // Update UI
        updateUi()
    }


    // Play recorded data fom current pointer position
    private fun play() {
        playbackStartPosition = (rawData.size * pointerPosition).toInt()
        // Rewind to 0 if at the end
        if (playbackStartPosition !in rawData.indices) {
            pointerPosition = 0F
            playbackStartPosition = 0
        }
        val dataToPlay = rawData.copyOfRange(playbackStartPosition, rawData.size)
        audioPlayer.playAudioFromRawData(dataToPlay)
        isPlayingNow = true
        modelData.setPlaybackState(true)
    }


    private fun stopPlaying() {
        audioPlayer.stop()
        isPlayingNow = false
        modelData.setPlaybackState(false)
    }


    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun loadFromFile(fileName: String) {

        // Open loading screen overlay
        modelData.setIsShowLoadingScreenOverlay(true)

        // Launch async task
        coroutineScope.launch {

            // Load
            resetData()

            // Lod data from the file
            val filePath = database.getSoundFileDirectoryPath() + "/" + fileName
            rawData = soundFile.readSoundFromFile(filePath,44100)

            // Process all loaded data
            processRawData()

            // Set UI
            modelData.setRecordingControlLayoutAsPlayer()
            modelData.openAllInfoPage()
            modelData.setMainMenuState(false)

            // Update UI
            audioProcessor.updateUi()

            // Close loading screen overlay
            modelData.setIsShowLoadingScreenOverlay(false)
        }
    }


    private fun updateUi() {
        modelData.setPointerPosition(pointerPosition)
    }


    private fun updateSoundFilesList() {
        soundFilesList = database.getAllSoundFilesInThePublicStorage()
        modelData.setRecordingFileList(soundFilesList)
    }


    private fun renameRecordingFile(fileName: String, newNameInput: String) {

        // Sanitize string
        val name = newNameInput.trim().replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")

        // Check
        if (name.isEmpty()) {
            modelData.openPopup("Error", "Name is too short") {}
            return
        }
        if (name.length > 48) {
            modelData.openPopup("Error", "Name is too long") {}
            return
        }
        if ("$name.wav" in soundFilesList) {
            modelData.openPopup("Error", "Name is already exists") {}
            return
        }

        // Rename (move)
        val oldFilePath = database.getSoundFileDirectoryPath() + "/" + fileName
        val newFilePath = database.getSoundFileDirectoryPath() + "/" + name + ".wav"
        database.moveFile(oldFilePath, newFilePath)
        updateSoundFilesList()
    }


    private fun deleteRecordingFile(fileName: String) {
        val filePath = database.getSoundFileDirectoryPath() + "/" + fileName
        database.deleteFile(filePath)
        updateSoundFilesList()
    }

}