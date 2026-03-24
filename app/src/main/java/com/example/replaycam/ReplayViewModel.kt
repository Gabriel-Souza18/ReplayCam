package com.example.replaycam

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * ViewModel responsável pela lógica de gravação segmentada e gerenciamento de replay.
 * 
 * Arquitetura:
 * - Mantém referência ao VideoSegmentManager
 * - Expõe LiveData para estado da gravação
 * - Gerencia rotação de segmentos a cada 5 segundos
 * - Salva arquivo final via MediaStore
 */
class ReplayViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "ReplayViewModel"
    }

    // Gerenciador de segmentos
    private val segmentManager = VideoSegmentManager(context)

    // LiveData para comunicação com UI
    private val _recordingState = MutableLiveData<RecordingState>(RecordingState.IDLE)
    val recordingState: LiveData<RecordingState> = _recordingState

    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage

    private val _segmentStats = MutableLiveData<String>()
    val segmentStats: LiveData<String> = _segmentStats

    // Estado de segmentação
    private var currentSegmentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Estados possíveis da gravação.
     */
    enum class RecordingState {
        IDLE,           // Não está gravando
        RECORDING,      // Gravando ativamente
        SAVING,         // Salvando replay
        ERROR           // Erro durante gravação
    }

    /**
     * Inicializa o ViewModel e começa preparação para gravação.
     */
    fun initialize() {
        Log.d(TAG, "ViewModel initialized")
        _recordingState.value = RecordingState.IDLE
        updateSegmentStats()
    }

    /**
     * Inicia a gravação segmentada (chamada após inicialização de CameraX).
     */
    fun startRecording() {
        if (_recordingState.value == RecordingState.RECORDING) {
            Log.w(TAG, "Already recording")
            return
        }
        _recordingState.value = RecordingState.RECORDING
        _statusMessage.value = "Gravando..."
        Log.d(TAG, "Recording started")
    }

    /**
     * Obtém o caminho para o próximo arquivo de segmento.
     * Chamado pelo CameraX VideoCapture.
     */
    fun getNextSegmentPath(): String {
        return segmentManager.getNextSegmentPath()
    }

    /**
     * Notifica ViewModel que um segmento foi salvo com sucesso.
     */
    fun onSegmentSaved(filePath: String) {
        Log.d(TAG, "Segment saved: $filePath")
        updateSegmentStats()
    }

    /**
     * Rotaciona para um novo segmento.
     * Isto seria chamado pelo VideoCapture callback.
     */
    fun rotateSegment() {
        currentSegmentIndex++
        updateSegmentStats()
        Log.d(TAG, "Segment rotated to index: $currentSegmentIndex")
    }

    /**
     * Salva o replay: concatena todos os segmentos e salva em MovieStore.
     */
    fun saveReplay() {
        if (_recordingState.value == RecordingState.SAVING) {
            Log.w(TAG, "Already saving")
            return
        }

        _recordingState.value = RecordingState.SAVING
        _statusMessage.value = "Salvando replay..."

        executor.execute {
            try {
                val segments = segmentManager.getCurrentSegments()
                if (segments.isEmpty()) {
                    updateUI(RecordingState.IDLE, "Nenhum segmento para salvar")
                    Log.w(TAG, "No segments to save")
                    return@execute
                }

                Log.d(TAG, "Starting save replay with ${segments.size} segments")

                // Criar arquivo temporário para concatenação
                val tempDir = context.cacheDir
                val concatenatedFile = File(
                    tempDir,
                    "replay_${System.currentTimeMillis()}.mp4"
                )

                // Concatenar segmentos
                val success = segmentManager.concatenateSegments(concatenatedFile)
                if (!success || !concatenatedFile.exists()) {
                    updateUI(RecordingState.IDLE, "Erro ao concatenar segmentos")
                    Log.e(TAG, "Concatenation failed")
                    return@execute
                }

                // Salvar em Movies
                val savedPath = saveToMoviesFolder(concatenatedFile)
                if (savedPath != null) {
                    // Limpar segmentos antigos
                    segmentManager.clearAllSegments()
                    updateUI(
                        RecordingState.IDLE,
                        "Replay salvo: $savedPath"
                    )
                    Log.d(TAG, "Replay saved successfully to: $savedPath")
                } else {
                    updateUI(RecordingState.IDLE, "Erro ao salvar em Movies")
                    Log.e(TAG, "Failed to save to Movies folder")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving replay: ${e.message}", e)
                updateUI(RecordingState.ERROR, "Erro: ${e.message}")
            }
        }
    }

    /**
     * Salva arquivo em Movies usando Environment API.
     * Retorna caminho completo do arquivo salvo ou null se falhar.
     */
    private fun saveToMoviesFolder(sourceFile: File): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "ReplayCam_$timestamp.mp4"

            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (!moviesDir.exists()) {
                moviesDir.mkdirs()
            }

            val destFile = File(moviesDir, fileName)
            sourceFile.copyTo(destFile, overwrite = true)

            // Notificar MediaStore sobre o novo arquivo
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )

            Log.d(TAG, "File saved to Movies: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to Movies folder: ${e.message}", e)
            null
        }
    }

    /**
     * Atualiza estatísticas de segmentos na UI.
     */
    private fun updateSegmentStats() {
        _segmentStats.value = segmentManager.getStats()
    }

    /**
     * Atualiza estado na thread principal.
     */
    private fun updateUI(state: RecordingState, message: String) {
        handler.post {
            _recordingState.value = state
            _statusMessage.value = message
        }
    }

    /**
     * Limpa recursos ao destruir ViewModel.
     */
    override fun onCleared() {
        super.onCleared()
        segmentManager.clearAllSegments()
        executor.shutdown()
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "ViewModel cleared")
    }
}



