/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.common.cleanUpMediapipeTaskErrorMessage
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "AGLlmChatModelHelper"

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit
typealias CleanUpListener = () -> Unit

sealed class ModelState {
  object NOT_INITIALIZED : ModelState()
  object INITIALIZING : ModelState()
  object INITIALIZED : ModelState()
  data class ERROR(val errorMessage: String?) : ModelState()
}

data class LlmModelInstance(val engine: LlmInference, var session: LlmInferenceSession)

object LlmChatModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()
  private val modelStates = mutableMapOf<String, MutableStateFlow<ModelState>>()

  fun getModelStateFlow(modelName: String): StateFlow<ModelState> {
    return modelStates.getOrPut(modelName) {
      MutableStateFlow(ModelState.NOT_INITIALIZED)
    }.asStateFlow()
  }

  fun initialize(
<<<<<<< HEAD
    context: Context, model: Model, onDone: (error: String) -> Unit
=======
    context: Context, model: Model
>>>>>>> f5fcdd17e36e2ee6297df131d2e26adea94e3c59
  ) {
    val modelStateFlow = modelStates.getOrPut(model.name) {
      MutableStateFlow(ModelState.NOT_INITIALIZED)
    }
    modelStateFlow.value = ModelState.INITIALIZING

    // Prepare options.
    val maxTokens =
      model.getIntConfigValue(key = ConfigKey.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val accelerator =
      model.getStringConfigValue(key = ConfigKey.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    Log.d(TAG, "Initializing...")
    // Force CPU for testing GPU OOM
    Log.d(TAG, "Forcing CPU backend for testing GPU OOM. Original accelerator config: $accelerator")
    val preferredBackend = LlmInference.Backend.CPU
    val options =
      LlmInference.LlmInferenceOptions.builder().setModelPath(model.getPath(context = context))
        .setMaxTokens(maxTokens).setPreferredBackend(preferredBackend)
        .setMaxNumImages(if (model.llmSupportImage) 1 else 0)
        .build()

    // Create an instance of the LLM Inference task and session.
    try {
      val llmInference = LlmInference.createFromOptions(context, options)

      val session = LlmInferenceSession.createFromOptions(
        llmInference,
        LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(topK).setTopP(topP)
          .setTemperature(temperature)
          .setGraphOptions(
            GraphOptions.builder().setEnableVisionModality(model.llmSupportImage).build()
          ).build()
      )
      model.instance = LlmModelInstance(engine = llmInference, session = session)
      modelStateFlow.value = ModelState.INITIALIZED
    } catch (e: Exception) {
      model.instance = null
      modelStateFlow.value = ModelState.ERROR(cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error"))
      return
    }
  }

  fun resetSession(model: Model) {
    try {
      Log.d(TAG, "Resetting session for model '${model.name}'")

      val instance = model.instance as LlmModelInstance? ?: return
      val session = instance.session
      session.close()

      val inference = instance.engine
      val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature =
        model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
      val newSession = LlmInferenceSession.createFromOptions(
        inference,
        LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(topK).setTopP(topP)
          .setTemperature(temperature)
          .setGraphOptions(
            GraphOptions.builder().setEnableVisionModality(model.llmSupportImage).build()
          ).build()
      )
      instance.session = newSession
      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset session", e)
    }
  }

  fun cleanUp(model: Model) {
    if (model.instance == null) {
      return
    }

    val instance = model.instance as LlmModelInstance
    try {
      // This will also close the session. Do not call session.close manually.
      instance.engine.close()
    } catch (e: Exception) {
      // ignore
    }
    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null
    modelStates[model.name]?.value = ModelState.NOT_INITIALIZED
    Log.d(TAG, "Clean up done.")
  }

  fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    image: Bitmap? = null,
  ) {
    val instance = model.instance as LlmModelInstance

    // Set listener.
    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    // Start async inference.
    //
    // For a model that supports image modality, we need to add the text query chunk before adding
    // image.
    val session = instance.session
    session.addQueryChunk(input)
    if (image != null) {
      session.addImage(BitmapImageBuilder(image).build())
    }
    session.generateResponseAsync(resultListener)
  }

  suspend fun runInferenceSuspending(
    model: Model,
    input: String,
    image: Bitmap? = null
  ): String {
    val instance = model.instance as? LlmModelInstance
      ?: throw IllegalStateException("Model instance is null. Initialize model first.")

    return suspendCancellableCoroutine { continuation ->
      val session = instance.session
      val resultBuilder = StringBuilder()

      try {
        // For a model that supports image modality, we need to add the text query chunk before adding image.
        session.addQueryChunk(input)
        if (image != null) {
          session.addImage(BitmapImageBuilder(image).build())
        }

        session.generateResponseAsync {
          partialResult, done ->
          resultBuilder.append(partialResult)
          if (done) {
            if (continuation.isActive) {
              continuation.resume(resultBuilder.toString().trim())
            }
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error during LlmInferenceSession.generateResponseAsync or setup", e)
        if (continuation.isActive) {
          continuation.resumeWithException(e)
        }
      }

      continuation.invokeOnCancellation {
        // Handle coroutine cancellation if needed, e.g., try to stop the inference if possible.
        // For LlmInferenceSession, it doesn't have an explicit cancel method for generateResponseAsync.
        // The session might clean up when it's closed or the LlmInference engine is closed.
        Log.d(TAG, "runInferenceSuspending coroutine was cancelled for input: ${input.take(50)}...")
      }
    }
  }
}
