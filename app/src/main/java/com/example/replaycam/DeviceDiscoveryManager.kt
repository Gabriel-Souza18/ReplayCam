package com.example.replaycam

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.InetAddress

class DeviceDiscoveryManager(context: Context, private val onDevicesFound: (List<Device>) -> Unit) {

    companion object {
        private const val TAG = "DeviceDiscoveryManager"
        private const val SERVICE_TYPE = "_replaycam._tcp."
    }

    private val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveredDevices = mutableListOf<Device>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        Log.d(TAG, "Iniciando descoberta de dispositivos")
        discoveredDevices.clear()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Descoberta iniciada: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Serviço encontrado: ${serviceInfo.serviceName}")

                // Resolver os detalhes do serviço para obter IP
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Falha ao resolver: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val ip = serviceInfo.host.hostAddress ?: return
                        val port = serviceInfo.port
                        val name = serviceInfo.serviceName

                        val device = Device(name = name, ip = ip, port = port)
                        if (!discoveredDevices.any { it.ip == ip }) {
                            discoveredDevices.add(device)
                            Log.d(TAG, "Dispositivo adicionado: $name ($ip:$port)")
                            onDevicesFound(discoveredDevices.toList())
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Serviço perdido: ${serviceInfo.serviceName}")
                discoveredDevices.removeIf { it.name == serviceInfo.serviceName }
                onDevicesFound(discoveredDevices.toList())
            }

            override fun onDiscoveryStopped(regType: String) {
                Log.d(TAG, "Descoberta parada: $regType")
            }

            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                Log.e(TAG, "Falha ao iniciar descoberta: $errorCode")
            }

            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                Log.e(TAG, "Falha ao parar descoberta: $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        Log.d(TAG, "Parando descoberta")
        discoveryListener?.let {
            nsdManager.stopServiceDiscovery(it)
        }
    }

    fun getDiscoveredDevices(): List<Device> = discoveredDevices.toList()
}

