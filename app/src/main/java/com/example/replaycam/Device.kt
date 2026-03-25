package com.example.replaycam

import java.io.Serializable

data class Device(
    val name: String,
    val ip: String,
    val port: Int = 5000,
    val id: String = name
) : Serializable

