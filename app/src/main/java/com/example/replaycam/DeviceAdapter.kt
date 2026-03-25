package com.example.replaycam

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

class DeviceAdapter(
    private var devices: List<Device> = emptyList(),
    private val onDeviceClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvDeviceIp: TextView = itemView.findViewById(R.id.tvDeviceIp)

        fun bind(device: Device) {
            tvDeviceName.text = device.name
            tvDeviceIp.text = device.ip
            itemView.setOnClickListener { onDeviceClick(device) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevices(newDevices: List<Device>) {
        devices = newDevices
        notifyDataSetChanged()
    }

    fun filterDevices(query: String) {
        devices = devices.filter { device ->
            device.name.contains(query, ignoreCase = true) ||
            device.ip.contains(query, ignoreCase = true)
        }
        notifyDataSetChanged()
    }
}


