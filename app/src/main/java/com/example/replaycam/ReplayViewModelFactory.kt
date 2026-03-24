package com.example.replaycam

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory para criar instâncias de ReplayViewModel com Context.
 * Necessário porque ReplayViewModel requer Context como parâmetro de construtor.
 */
class ReplayViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReplayViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReplayViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

