package com.example.replaycam

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class RoleSelectionDialog(
    context: Context,
    private val deviceName: String,
    private val onRoleSelected: (role: UserType) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_role_selection)

        val tvMessage: TextView = findViewById(R.id.tvMessage)
        val btnCamera: Button = findViewById(R.id.btnCamera)
        val btnButton: Button = findViewById(R.id.btnButton)

        tvMessage.text = "Conectando a: $deviceName\n\nEscolha seu papel:"

        btnCamera.setOnClickListener {
            onRoleSelected(UserType.CAM)
            dismiss()
        }

        btnButton.setOnClickListener {
            onRoleSelected(UserType.BUTTON)
            dismiss()
        }
    }
}

