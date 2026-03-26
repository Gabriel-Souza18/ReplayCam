package com.example.replaycam

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var etSearch: EditText
    private lateinit var rvDevices: RecyclerView
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvNoDevices: TextView
    private lateinit var btnRetry: Button
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var discoveryManager: DeviceDiscoveryManager
    private var allDevices: List<Device> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        startDeviceDiscovery()
    }

    private fun initViews() {
        etSearch = findViewById(R.id.etSearch)
        rvDevices = findViewById(R.id.rvDevices)
        pbLoading = findViewById(R.id.pbLoading)
        tvNoDevices = findViewById(R.id.tvNoDevices)
        btnRetry = findViewById(R.id.btnRetry)

        // Configurar busca/filtro
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterDevices(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Botão tentar novamente
        btnRetry.setOnClickListener {
            startDeviceDiscovery()
        }
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(emptyList()) { device ->
            onDeviceSelected(device)
        }

        rvDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
    }

    private fun startDeviceDiscovery() {
        Log.d(TAG, "Iniciando descoberta de dispositivos")
        pbLoading.visibility = ProgressBar.VISIBLE
        rvDevices.visibility = RecyclerView.GONE
        tvNoDevices.visibility = TextView.GONE
        btnRetry.visibility = Button.GONE

        discoveryManager = DeviceDiscoveryManager(this) { devices ->
            allDevices = devices
            runOnUiThread {
                updateUI(devices)
            }
        }

        discoveryManager.startDiscovery()

        // Timeout de 10 segundos para descoberta
        Thread {
            Thread.sleep(10000)
            runOnUiThread {
                if (allDevices.isEmpty()) {
                    pbLoading.visibility = ProgressBar.GONE
                    tvNoDevices.visibility = TextView.VISIBLE
                    btnRetry.visibility = Button.VISIBLE
                }
            }
        }.start()
    }

    private fun updateUI(devices: List<Device>) {
        pbLoading.visibility = ProgressBar.GONE

        if (devices.isEmpty()) {
            rvDevices.visibility = RecyclerView.GONE
            tvNoDevices.visibility = TextView.VISIBLE
            btnRetry.visibility = Button.VISIBLE
        } else {
            rvDevices.visibility = RecyclerView.VISIBLE
            tvNoDevices.visibility = TextView.GONE
            btnRetry.visibility = Button.GONE
            deviceAdapter.updateDevices(devices)
        }
    }

    private fun filterDevices(query: String) {
        val filtered = allDevices.filter { device ->
            device.name.contains(query, ignoreCase = true) ||
            device.ip.contains(query, ignoreCase = true)
        }

        if (query.isEmpty()) {
            deviceAdapter.updateDevices(allDevices)
        } else {
            deviceAdapter.updateDevices(filtered)
        }
    }

    private fun onDeviceSelected(device: Device) {
        Log.d(TAG, "Dispositivo selecionado: ${device.name}")

        // Mostrar diálogo de seleção de role
        val dialog = RoleSelectionDialog(this, device.name) { role ->
            handleRoleSelected(device, role)
        }
        dialog.show()
    }

    private fun handleRoleSelected(device: Device, role: UserType) {
        Log.d(TAG, "Role selecionado: $role para dispositivo ${device.name}")

        // Conectar ao dispositivo e enviar role escolhido
        val connectionManager = ConnectionManager(device) { isConnected, message ->
            Log.d(TAG, "Status conexão: $isConnected - $message")
        }

        connectionManager.connectAndSendRole(role)

        // Aqui você pode navegar para a próxima tela (Câmera ou Botão)
        // Por enquanto, apenas log
    }

    override fun onDestroy() {
        super.onDestroy()
        discoveryManager.stopDiscovery()
    }

    public fun gravar(v: View){
        val intent = Intent(this, cameraActivity::class.java)
        startActivity(intent)
    }


}
