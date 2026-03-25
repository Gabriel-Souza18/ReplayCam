package com.example.replaycam

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class ConnectionManager(
    private val device: Device,
    private val onConnectionStatus: (isConnected: Boolean, message: String) -> Unit
) {

    companion object {
        private const val TAG = "ConnectionManager"
    }

    private var socket: Socket? = null
    private var isConnected = false

    fun connectAndSendRole(role: UserType) {
        Thread {
            try {
                Log.d(TAG, "Conectando a ${device.ip}:${device.port}")
                socket = Socket(device.ip, device.port)
                isConnected = true

                val writer = OutputStreamWriter(socket!!.outputStream)
                writer.write("ROLE:${role.name}")
                writer.flush()

                onConnectionStatus(true, "Conectado a ${device.name}")
                Log.d(TAG, "Enviado role: ${role.name}")

                // Aguarda resposta do outro dispositivo
                val reader = BufferedReader(InputStreamReader(socket!!.inputStream))
                val response = reader.readLine()
                Log.d(TAG, "Resposta recebida: $response")

            } catch (e: Exception) {
                isConnected = false
                Log.e(TAG, "Erro na conexão: ${e.message}")
                onConnectionStatus(false, "Falha ao conectar: ${e.message}")
            }
        }.start()
    }

    fun disconnect() {
        try {
            socket?.close()
            isConnected = false
            Log.d(TAG, "Desconectado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desconectar: ${e.message}")
        }
    }

    fun isConnected(): Boolean = isConnected
}

