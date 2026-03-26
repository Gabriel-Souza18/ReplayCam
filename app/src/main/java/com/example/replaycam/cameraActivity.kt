package com.example.replaycam

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class cameraActivity : AppCompatActivity() {
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var videoCapture: VideoCapture<Recorder>? = null
    private var isRecording = false
    private lateinit var recordButton: Button
    private lateinit var saveButton: Button
    private lateinit var stopButton: Button
    private lateinit var recordingIndicator: View
    private lateinit var videoSegmentManager: VideoSegmentManager
    private var currentRecording: androidx.camera.video.Recording? = null
    private var recordingSessionCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera)
        
        recordButton = findViewById(R.id.recordButton)
        saveButton = findViewById(R.id.saveButton)
        stopButton = findViewById(R.id.stopButton)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        videoSegmentManager = VideoSegmentManager(this)

        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(
                this, CAMERAX_PERMISSIONS, PERMISSION_REQUEST_CODE
            )
        } else {
            startCamera()
        }

        // Botão REC: Iniciar gravação (quando não está gravando)
        recordButton.setOnClickListener {
            if (!isRecording) {
                startRecording()
            }
        }

        // Botão SALVAR: Concatenar segmentos SEM parar a gravação
        saveButton.setOnClickListener {
            saveAndContinueRecording()
        }

        // Botão PARAR: Parar a gravação completamente
        stopButton.setOnClickListener {
            stopRecording()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!isRecording) {
                    saveAndContinueRecording()
                } else {
                    saveAndContinueRecording()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
    
    private fun startCamera() {
        Log.d(TAG, "🎥 Iniciando câmera()")
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val previewView = findViewById<PreviewView>(R.id.previewView)

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                Log.d(TAG, "✅ Câmera iniciada com sucesso")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao inicializar câmera", e)
                showToast("Erro ao inicializar câmera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startRecording() {
        Log.d(TAG, "🔴 Iniciando gravação")

        val videoCapture = videoCapture ?: run {
            Log.e(TAG, "❌ VideoCapture não inicializado")
            showToast("Câmera não está pronta")
            return
        }

        try {
            val recordingPath = videoSegmentManager.getNextSegmentPath()
            val recordingFile = File(recordingPath)
            
            Log.d(TAG, "📁 Gravando em: $recordingPath")

            val outputOptions = androidx.camera.video.FileOutputOptions.Builder(recordingFile)
                .build()

            val recordingBuilder = videoCapture.output
                .prepareRecording(this, outputOptions)
            
            val recordingBuilderWithAudio = if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                recordingBuilder.withAudioEnabled()
            } else {
                recordingBuilder
            }

            currentRecording = recordingBuilderWithAudio
                .start(ContextCompat.getMainExecutor(this)) { videoRecordEvent ->
                    when (videoRecordEvent) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            recordingSessionCount++
                            updateButtonStates()
                            recordingIndicator.visibility = View.VISIBLE
                            Log.d(TAG, "⏹️  Gravação iniciada (sessão #$recordingSessionCount)")
                        }
                        
                        is VideoRecordEvent.Pause -> {
                            Log.d(TAG, "⏸️  Gravação pausada")
                        }
                        
                        is VideoRecordEvent.Resume -> {
                            Log.d(TAG, "▶️  Gravação retomada")
                        }
                        
                        is VideoRecordEvent.Finalize -> {
                            if (!videoRecordEvent.hasError()) {
                                Log.d(TAG, "✅ Segmento salvo com sucesso")
                                Log.d(TAG, videoSegmentManager.getStats())
                            } else {
                                Log.e(TAG, "❌ Erro ao gravar: ${videoRecordEvent.error}")
                                showToast("Erro ao gravar: ${videoRecordEvent.error}")
                            }
                        }
                        
                        else -> {}
                    }
                }
            
            showToast("🔴 Gravação iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao iniciar gravação", e)
            showToast("Erro ao iniciar gravação: ${e.message}")
            isRecording = false
        }
    }

    /**
     * Salva e concatena os segmentos MANTENDO a gravação ativa
     */
    private fun saveAndContinueRecording() {
        Log.d(TAG, "💾 Salvando segmentos (continuando gravação)...")
        
        try {
            val segments = videoSegmentManager.getCurrentSegments()
            if (segments.isEmpty()) {
                showToast("Nenhum segmento para salvar")
                return
            }

            // Criar arquivo final com timestamp
            val timestamp = System.currentTimeMillis()
            val finalFile = File(
                videoSegmentManager.getOutputDirectory(),
                "replay_final_${timestamp}.mp4"
            )

            val concatenated = videoSegmentManager.concatenateSegments(finalFile)
            
            if (concatenated) {
                Log.d(TAG, "✅ Vídeo salvo: ${finalFile.absolutePath}")
                showToast("✅ Vídeo salvo em DCIM/ReplayCam\n${finalFile.name}")
                
                // Limpar segmentos para próxima sessão
                videoSegmentManager.clearAllSegments()
                recordingSessionCount = 0
            } else {
                Log.e(TAG, "❌ Erro ao concatenar segmentos")
                showToast("Erro ao salvar vídeo")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao salvar: ${e.message}", e)
            showToast("Erro ao salvar: ${e.message}")
        }
    }

    /**
     * Para a gravação completamente
     */
    private fun stopRecording() {
        Log.d(TAG, "⏹️  Parando gravação")
        
        try {
            currentRecording?.stop()
            isRecording = false
            recordingSessionCount = 0
            updateButtonStates()
            recordingIndicator.visibility = View.GONE
            showToast("Gravação parada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao parar gravação", e)
            showToast("Erro ao parar: ${e.message}")
        }
    }

    /**
     * Atualiza visibilidade dos botões de acordo com o estado
     */
    private fun updateButtonStates() {
        if (isRecording) {
            // Durante gravação: esconde REC, mostra SALVAR e PARAR
            recordButton.visibility = View.GONE
            saveButton.visibility = View.VISIBLE
            stopButton.visibility = View.VISIBLE
        } else {
            // Não gravando: mostra REC, esconde SALVAR e PARAR
            recordButton.visibility = View.VISIBLE
            saveButton.visibility = View.GONE
            stopButton.visibility = View.GONE
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        } && hasStoragePermissions()
    }

    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                baseContext,
                android.Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                baseContext,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startCamera()
            } else {
                showToast("Permissões necessárias são obrigatórias")
                finish()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "CameraActivity"
        private val CAMERAX_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        private const val PERMISSION_REQUEST_CODE = 101
    }
}