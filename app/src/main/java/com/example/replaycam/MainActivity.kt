package com.example.replaycam

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MainActivity: Gerencia a interface de câmera e interação com ViewModel.
 * 
 * Fluxo:
 * 1. Solicita permissões em runtime (CAMERA, RECORD_AUDIO)
 * 2. Inicializa CameraX com Preview e VideoCapture
 * 3. Inicia gravação de segmentos de 5 segundos automaticamente
 * 4. Ao clicar em "Salvar Replay", dispara concatenação e salvamento
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val SEGMENT_DURATION_MS = 5000L  // 5 segundos
    }

    // ViewModel
    private val viewModel: ReplayViewModel by viewModels { ReplayViewModelFactory(this) }

    // UI Components
    private lateinit var previewView: PreviewView
    private lateinit var btnSaveReplay: MaterialButton

    // CameraX
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var isRecording = false

    // Executor
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Setup padding para edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar UI
        previewView = findViewById(R.id.preview)
        btnSaveReplay = findViewById(R.id.btnSaveReplay)

        // Inicializar ViewModel
        viewModel.initialize()

        // Observar estado de gravação
        viewModel.recordingState.observe(this) { state ->
            updateUIState(state)
        }

        // Observar mensagens de status
        viewModel.statusMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            Log.d(TAG, message)
        }

        // Observar estatísticas de segmentos
        viewModel.segmentStats.observe(this) { stats ->
            Log.d(TAG, "Segment stats: $stats")
        }

        // Setup button listener
        btnSaveReplay.setOnClickListener {
            viewModel.saveReplay()
        }

        // Verificar e solicitar permissões
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    /**
     * Verifica se todas as permissões necessárias foram concedidas.
     */
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Solicita permissões em tempo de execução.
     */
    private val permissionRequestLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                Log.d(TAG, "All permissions granted")
                startCamera()
            } else {
                Log.e(TAG, "Some permissions were denied")
                Toast.makeText(this, "Permissões necessárias não concedidas", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    /**
     * Solicita permissões através do launcher registrado.
     */
    private fun requestPermissions() {
        permissionRequestLauncher.launch(REQUIRED_PERMISSIONS)
    }

    /**
     * Inicializa a câmera usando CameraX.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                try {
                    cameraProvider = cameraProviderFuture.get()

                    // Setup Preview
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    // Setup VideoCapture com Recorder
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                        .build()

                    videoCapture = VideoCapture.withOutput(recorder)

                    // Selecionar câmera traseira
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    // Vincular casos de uso à lifecycle
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        videoCapture
                    )

                    // Iniciar gravação segmentada
                    startSegmentedRecording()

                    Log.d(TAG, "Camera initialized successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing camera: ${e.message}", e)
                    Toast.makeText(this, "Erro ao inicializar câmera", Toast.LENGTH_SHORT).show()
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    /**
     * Inicia gravação segmentada: rotaciona segmentos de 5 segundos continuamente.
     */
    @SuppressLint("MissingPermission")
    private fun startSegmentedRecording() {
        if (isRecording || videoCapture == null) {
            Log.w(TAG, "Already recording or videoCapture not initialized")
            return
        }

        isRecording = true
        viewModel.startRecording()

        Thread {
            try {
                while (isRecording) {
                    // Obter caminho para novo segmento
                    val segmentPath = viewModel.getNextSegmentPath()
                    val outputFile = File(segmentPath)

                    Log.d(TAG, "Starting segment: $segmentPath")

                    // Criar FileOutputOptions
                    val fileOutputOptions = FileOutputOptions.Builder(outputFile).build()

                    // Iniciar gravação de segmento
                    val recording = videoCapture?.output
                        ?.prepareRecording(this, fileOutputOptions)
                        ?.withAudioEnabled()
                        ?.start(cameraExecutor) { videoRecordEvent ->
                            when (videoRecordEvent) {
                                is VideoRecordEvent.Start -> {
                                    Log.d(TAG, "Recording started: ${outputFile.name}")
                                }
                                is VideoRecordEvent.Finalize -> {
                                    if (!videoRecordEvent.hasError()) {
                                        Log.d(TAG, "Segment saved: ${outputFile.name}")
                                        viewModel.onSegmentSaved(segmentPath)
                                    } else {
                                        Log.e(TAG, "Recording error: ${videoRecordEvent.error}")
                                    }
                                }
                                else -> {}
                            }
                        }

                    // Gravar por 5 segundos, depois rotacionar
                    Thread.sleep(SEGMENT_DURATION_MS)

                    // Parar gravação atual (será finalizado automaticamente)
                    recording?.stop()

                    Log.d(TAG, "Segment rotation triggered")
                    viewModel.rotateSegment()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in segmented recording: ${e.message}", e)
                isRecording = false
            }
        }.start()
    }

    /**
     * Atualiza UI baseado no estado de gravação.
     */
    private fun updateUIState(state: ReplayViewModel.RecordingState) {
        when (state) {
            ReplayViewModel.RecordingState.IDLE -> {
                btnSaveReplay.isEnabled = true
                btnSaveReplay.text = getString(R.string.save_replay)
            }
            ReplayViewModel.RecordingState.RECORDING -> {
                btnSaveReplay.isEnabled = true
            }
            ReplayViewModel.RecordingState.SAVING -> {
                btnSaveReplay.isEnabled = false
                btnSaveReplay.text = "Salvando..."
            }
            ReplayViewModel.RecordingState.ERROR -> {
                btnSaveReplay.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        cameraExecutor.shutdown()
    }
}
