package com.google.ai.edge.gallery.services

import android.content.Context
import android.util.Log
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import kotlinx.coroutines.flow.StateFlow

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
    }

    fun initializeModelAsync(model: Model, onDone: (Boolean) -> Unit) {
        try {
            LlmChatModelHelper.initialize(context, model) { result ->
                if (result.isEmpty()) {
                    Log.d(TAG, "Model ${model.name} initialized successfully.")
                    onDone(true)
                } else {
                    Log.e(TAG, "Model ${model.name} initialization failed: $result")
                    onDone(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model ${model.name}", e)
            onDone(false)
        }
    }

    fun observeModelState(model: Model): StateFlow<com.google.ai.edge.gallery.ui.llmchat.ModelState> {
        return LlmChatModelHelper.getModelStateFlow(model.name)
    }
}