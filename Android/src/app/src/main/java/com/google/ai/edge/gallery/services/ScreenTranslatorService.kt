package com.google.ai.edge.gallery.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build as AndroidBuild
import android.app.Activity
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.media.Image
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ContextThemeWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text // Added for Text.TextBlock parameter
import android.graphics.Bitmap
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper // Ensure this is present
import com.google.ai.edge.gallery.ui.llmchat.ModelState // Import for explicit type usage
import com.google.ai.edge.gallery.utils.OverlayManager
import android.os.Handler
import com.google.ai.edge.gallery.data.PREF_TARGET_LANGUAGE
import com.google.ai.edge.gallery.data.DEFAULT_TARGET_LANGUAGE
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.ai.edge.gallery.helpers.NotificationHelper
import com.google.ai.edge.gallery.helpers.MediaProjectionHelper
import com.google.ai.edge.gallery.helpers.ImageProcessingHelper
import com.google.ai.edge.gallery.helpers.OverlayHelper
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.graphics.Rect
import android.os.Build
import androidx.core.content.getSystemService

// Refatorar ScreenTranslatorService para usar as classes auxiliares
class ScreenTranslatorService : Service() {

    // Floating Action Button variables
    private var windowManager: WindowManager? = null
    private var floatingButtonView: View? = null
    private var floatingButtonParams: WindowManager.LayoutParams? = null

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var mediaProjectionHelper: MediaProjectionHelper
    private lateinit var imageProcessingHelper: ImageProcessingHelper
    private lateinit var overlayHelper: OverlayHelper

    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding, so return null
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    // For screen dimensions
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    private var llmChatModelHelper: LlmChatModelHelper? = null // Retaining this as it might be used elsewhere, though not for init directly
    private var selectedGemmaModel: Model? = null
    private var overlayManager: OverlayManager? = null
    private lateinit var imageReaderHandler: Handler
    private val isProcessingFrame = AtomicBoolean(false)
    private val captureRequested = AtomicBoolean(false)
    private val translationCache = mutableMapOf<String, String>() // Added translation cache
    private lateinit var sharedPreferences: SharedPreferences
    internal var currentTargetLanguage: String = DEFAULT_TARGET_LANGUAGE // Made internal for testing

    // Coroutine scope for the service, tied to its lifecycle.
    // Use SupervisorJob so if one child coroutine fails, others are not affected.
    private val serviceJob = SupervisorJob()
    // Default dispatcher is Main, suitable for launching UI-related or main-thread-bound tasks.
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        // private const val MODEL_INIT_TIMEOUT_MS = 30000L // Removed, direct check of StateFlow.value
        const val CHANNEL_ID = "ScreenTranslatorChannel"
        const val ONGOING_NOTIFICATION_ID = 1001 // Example ID
        private const val TAG = "ScreenTranslatorService" // For logging
        const val EXTRA_RESULT_CODE = "mp_result_code"
        const val EXTRA_DATA_INTENT = "mp_data_intent"

        // Intent extras for passing model details
        const val EXTRA_MODEL_NAME = "st_model_name"
        const val EXTRA_MODEL_VERSION = "st_model_version"
        const val EXTRA_MODEL_DOWNLOAD_FILE_NAME = "st_model_download_file_name"
        const val EXTRA_MODEL_URL = "st_model_url"
        const val EXTRA_MODEL_SIZE_BYTES = "st_model_size_bytes"
        const val EXTRA_MODEL_IMPORTED = "st_model_imported"
        const val EXTRA_MODEL_LLM_SUPPORT_IMAGE = "st_model_llm_support_image"
        const val EXTRA_MODEL_NORMALIZED_NAME = "st_model_normalized_name"
        // Add other fields from Model class as needed, e.g., info, learnMoreUrl, configs etc.
        // For now, focusing on core fields for loading and basic operation.

        const val ACTION_CAPTURE_AND_TRANSLATE = "ACTION_CAPTURE_AND_TRANSLATE" // Action for FAB
        // var isProcessingEnabled = true // Removed: No longer controlling processing this way
    }

    private lateinit var modelManager: ModelManager
    private lateinit var imageProcessor: ImageProcessor

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenTranslatorService onCreate. Service instance: $this")

        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel(ScreenTranslatorService.CHANNEL_ID) // Pass CHANNEL_ID here

        mediaProjectionHelper = MediaProjectionHelper(this)
        imageProcessingHelper = ImageProcessingHelper()
        overlayHelper = OverlayHelper(this)

        llmChatModelHelper = LlmChatModelHelper
        overlayManager = OverlayManager(this) // Removed the extra 'channelId' argument
        imageReaderHandler = Handler(Looper.getMainLooper())

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        // Load the target language
        currentTargetLanguage = sharedPreferences.getString(PREF_TARGET_LANGUAGE, DEFAULT_TARGET_LANGUAGE) ?: DEFAULT_TARGET_LANGUAGE
        Log.i(TAG, "Loaded target language: $currentTargetLanguage")


        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (AndroidBuild.VERSION.SDK_INT >= AndroidBuild.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
            screenDensity = resources.displayMetrics.densityDpi
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION") // Default display and getMetrics are deprecated but needed for older APIs
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDensity = displayMetrics.densityDpi
        }
        Log.d(TAG, "Screen dimensions: $screenWidth x $screenHeight @ $screenDensity dpi")

        // Initialize WindowManager here as it's definitely needed if we show the button
        this.windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showFloatingButton()

        modelManager = ModelManager(this)
        imageProcessor = ImageProcessor()
    }

    private fun prepareGemmaModel() {
        // This function is now called AFTER attempting to load model from intent extras.
        // So, if selectedGemmaModel is still null here, it means no model was passed via intent,
        // or the service was started in a way that didn't include model details.
        // In such cases, we fall back to the hardcoded placeholder.
        if (selectedGemmaModel == null) {
            Log.d(TAG, "No model passed via intent, creating hardcoded placeholder selectedGemmaModel.")
            selectedGemmaModel = Model(
                name = "Gemma-3n-E2B-it-int4",
                url = "google/gemma-3n-E2B-it-litert-preview",
                downloadFileName = "gemma-3n-E2B-it-int4.task",
                info = "Placeholder Gemma model for ScreenTranslatorService (fallback)",
                sizeInBytes = 0L,
                version = "internal",
                llmSupportImage = false // Default for placeholder
            )
            // normalizedName will be set by Model's init block
            Log.d(TAG, "Created placeholder selectedGemmaModel: ${selectedGemmaModel?.name}")
        } else {
            Log.d(TAG, "Using selectedGemmaModel provided: ${selectedGemmaModel?.name}")
        }

        val currentModel = selectedGemmaModel
        if (currentModel != null) {
            Log.d(TAG, "prepareGemmaModel: Initializing or observing model: ${currentModel.name}")
            // Trigger initialization (it's idempotent in LlmChatModelHelper if already INITIALIZING or INITIALIZED)
            LlmChatModelHelper.initialize(applicationContext, currentModel, onDone)

            // Launch a coroutine to observe the model's state
            serviceScope.launch {
                LlmChatModelHelper.getModelStateFlow(currentModel.name).collect { state ->
                    // Only log if this is still the selected model to avoid spam from old model flows
                    if (selectedGemmaModel == currentModel) {
                        when (state) {
                            is com.google.ai.edge.gallery.ui.llmchat.ModelState.INITIALIZED -> {
                                Log.i(TAG, "Gemma model '${currentModel.name}' is INITIALIZED. Instance: ${currentModel.instance}")
                            }
                            is com.google.ai.edge.gallery.ui.llmchat.ModelState.ERROR -> {
                                Log.e(TAG, "Gemma model '${currentModel.name}' initialization ERROR: ${state.errorMessage}")
                            }
                            is com.google.ai.edge.gallery.ui.llmchat.ModelState.INITIALIZING -> {
                                Log.i(TAG, "Gemma model '${currentModel.name}' is INITIALIZING.")
                            }
                            is com.google.ai.edge.gallery.ui.llmchat.ModelState.NOT_INITIALIZED -> {
                                Log.i(TAG, "Gemma model '${currentModel.name}' is NOT_INITIALIZED.")
                            }
                        }
                    } else {
                        Log.d(TAG, "Received state update for a non-selected model ('${currentModel.name}'), ignoring. Current is '${selectedGemmaModel?.name}'.")
                        // Optionally, ensure this collector cancels if the model it's observing is no longer selected.
                        // However, `collect` will run indefinitely. A better approach might be to manage the Job of this collector
                        // and cancel/restart it when `selectedGemmaModel` changes.
                        // For now, the if condition provides some safety.
                        this.cancel() // Cancel this specific collector coroutine if the model is no longer selected.
                    }
                }
            }
        } else {
            Log.d(TAG, "No model to initialize or observe.")
        }
    }

    // Update the onDone callback to match the expected type
    private val onDone: (String) -> Unit = { result ->
        if (result.isEmpty()) {
            Log.d(TAG, "Model initialization completed successfully.")
        } else {
            Log.e(TAG, "Model initialization failed with error: $result")
        }
    }

    private suspend fun initializeModelAsync(model: Model): Boolean {
        return try {
            LlmChatModelHelper.initialize(applicationContext, model) { result ->
                if (result.isEmpty()) {
                    Log.d(TAG, "Model ${model.name} initialized successfully.")
                } else {
                    Log.e(TAG, "Model ${model.name} initialization failed: $result")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model ${model.name}", e)
            false
        }
    }

    private fun optimizeImageProcessing(reader: ImageReader) {
        reader.setOnImageAvailableListener({ reader ->
            // Só processa se uma captura foi solicitada
            if (!captureRequested.get()) {
                // Descarta a imagem se não foi solicitada
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            serviceScope.launch(Dispatchers.IO) {
                if (isProcessingFrame.compareAndSet(false, true)) {
                    var image: Image? = null
                    var bitmap: Bitmap? = null
                    
                    try {
                        image = reader.acquireLatestImage()
                        image?.let { img ->
                            // Usa a versão consolidada do ImageProcessingHelper
                            bitmap = imageProcessingHelper.processImageToBitmap(img, screenWidth, screenHeight)
                            bitmap?.let { bmp ->
                                processImageForTranslation(bmp)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao processar imagem", e)
                    } finally {
                        // Garante limpeza de recursos
                        image?.close()
                        bitmap?.recycle()
                        isProcessingFrame.set(false)
                        captureRequested.set(false) // Reset após processamento
                    }
                } else {
                    // Se já está processando, descarta a imagem atual
                    reader.acquireLatestImage()?.close()
                }
            }
        }, imageReaderHandler)
    }

    private fun processImageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * screenWidth

        val bitmapWidth = screenWidth + rowPadding / pixelStride
        val tempBitmap = Bitmap.createBitmap(bitmapWidth, screenHeight, Bitmap.Config.ARGB_8888)
        tempBitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            Bitmap.createBitmap(tempBitmap, 0, 0, screenWidth, screenHeight).also {
                tempBitmap.recycle()
            }
        } else {
            tempBitmap
        }
    }

    private fun initializeSelectedModel() {
        selectedGemmaModel?.let { model ->
            modelManager.initializeModelAsync(model) { success ->
                if (success) {
                    Log.d(TAG, "Model ${model.name} initialized successfully.")
                } else {
                    Log.e(TAG, "Failed to initialize model: ${model.name}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called")

        val notification = notificationHelper.createForegroundNotification(CHANNEL_ID)
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        // Handle specific actions first
        if (intent?.action == ACTION_CAPTURE_AND_TRANSLATE) {
            Log.d(TAG, "onStartCommand received ACTION_CAPTURE_AND_TRANSLATE")
            triggerCaptureAndTranslate()
            // After handling the action, we still want to ensure MediaProjection is set up if needed,
            // or that the service continues running as sticky.
            // So, we don't return immediately here unless this action is mutually exclusive
            // with MediaProjection setup, which it isn't necessarily.
        }

        // Existing logic for MediaProjection setup
        if (intent?.hasExtra(EXTRA_RESULT_CODE) == true && intent.hasExtra(EXTRA_DATA_INTENT)) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>(EXTRA_DATA_INTENT) // Use getParcelableExtra with type for API 33+
            if (resultCode == Activity.RESULT_OK && data != null) {
                mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
                if (mediaProjection != null) {
                    Log.i(TAG, "MediaProjection obtained.")
                    // Register a callback to stop the service if projection stops
                    // MUST be registered BEFORE createVirtualDisplay() is called (inside setupImageReaderAndVirtualDisplay)
                    mediaProjection?.registerCallback(this.mediaProjectionCallback, null) // Handler can be null

                    // Attempt to load model from intent BEFORE calling prepareGemmaModel
                    if (intent.hasExtra(EXTRA_MODEL_NAME)) {
                        try {
                            Log.d(TAG, "onCreate: Attempting to retrieve model from intent.")
                            val modelName = intent.getStringExtra(EXTRA_MODEL_NAME)
                            if (modelName == null) {
                                Log.e(TAG, "onCreate: Model name is null in intent. Cannot initialize Gemma.")
                                // Optionally stop the service or handle error appropriately
                                stopSelf()
                                return START_STICKY
                            }
                            Log.d(TAG, "onCreate: Received modelName from intent: $modelName")
                            val modelVersion = intent.getStringExtra(EXTRA_MODEL_VERSION) ?: "_"
                            val modelDownloadFileName = intent.getStringExtra(EXTRA_MODEL_DOWNLOAD_FILE_NAME)!!
                            val modelUrl = intent.getStringExtra(EXTRA_MODEL_URL)!!
                            val modelSizeBytes = intent.getLongExtra(EXTRA_MODEL_SIZE_BYTES, 0L)
                            val modelImported = intent.getBooleanExtra(EXTRA_MODEL_IMPORTED, false)
                            val modelLlmSupportImage = intent.getBooleanExtra(EXTRA_MODEL_LLM_SUPPORT_IMAGE, false)
                            // val modelNormalizedName = intent.getStringExtra(EXTRA_MODEL_NORMALIZED_NAME) // Not needed to pass, Model init handles it

                            selectedGemmaModel = Model(
                                name = modelName,
                                version = modelVersion,
                                downloadFileName = modelDownloadFileName,
                                url = modelUrl,
                                sizeInBytes = modelSizeBytes,
                                imported = modelImported,
                                llmSupportImage = modelLlmSupportImage
                                // Other fields like info, learnMoreUrl, configs can be added if needed
                            )
                            Log.d(TAG, "Successfully created selectedGemmaModel from intent extras: ${selectedGemmaModel?.name}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error creating Model from intent extras. Will use placeholder if prepareGemmaModel creates one.", e)
                            selectedGemmaModel = null // Ensure fallback if parsing fails
                        }
                    } // Closes if (intent.hasExtra(EXTRA_MODEL_NAME))
                    // Removed extraneous else block that was here

                    prepareGemmaModel() // Now call prepareGemmaModel
                    setupImageReaderAndVirtualDisplay()
                } else {
                    Log.e(TAG, "Failed to obtain MediaProjection.")
                    stopSelf() // Stop if we can't get projection
                }
            } else {
                Log.e(TAG, "MediaProjection permission not granted or data intent missing.")
                stopSelf() // Stop if permission not granted
            }
        } else {
            // If service is started without projection data (e.g. first start to show notification, or restart)
            // This path should ideally not try to start projection, or be handled carefully.
            // For now, if it's not the intent from ActivityResult, it won't have the extras.
            Log.d(TAG, "Service started without MediaProjection data.")
        }

        serviceScope.launch {
            initializeSelectedModel()
        }

        // If the service is killed, it will be automatically restarted.
        return START_STICKY
    }
    
    private fun setupImageReaderAndVirtualDisplay() {
        Log.d(TAG, "Setting up ImageReader and VirtualDisplay.")
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            serviceScope.launch(Dispatchers.IO) {
                if (isProcessingFrame.compareAndSet(false, true)) {
                    var image: Image? = null
                    try {
                        image = reader.acquireLatestImage()
                        image?.let {
                            val bitmap = processImageToBitmap(it)
                            processImageForTranslation(bitmap)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing image", e)
                    } finally {
                        image?.close()
                        isProcessingFrame.set(false)
                    }
                }
            }, imageReaderHandler)

        // Create VirtualDisplay after setting the ImageReader listener
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenTranslatorVirtualDisplay",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, // Corrected type mismatch
            imageReader?.surface,
            null, // No callback for now
            null // Handler can be null
        )

        Log.d(TAG, "VirtualDisplay created: $virtualDisplay")
    }

    private fun showFloatingButton() {
        Log.d(TAG, "Initializing floating button.")
        floatingButtonView = View(this).apply {
            setBackgroundResource(R.drawable.ic_floating_button)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    triggerCaptureAndTranslate()
                }
                true
            }
        }

        floatingButtonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (AndroidBuild.VERSION.SDK_INT >= AndroidBuild.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager?.addView(floatingButtonView, floatingButtonParams)
    }

    private fun triggerCaptureAndTranslate() {
        Log.d(TAG, "Capture and translate triggered.")
        captureRequested.set(true)
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped.")
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            stopSelf()
        }
    }

    private fun processImageForTranslation(bitmap: Bitmap) {
        Log.d(TAG, "Processando imagem para tradução")
        
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                if (visionText.text.isNotBlank()) {
                    Log.d(TAG, "Texto reconhecido: ${visionText.text}")
                    
                    // Processa cada bloco de texto
                    visionText.textBlocks.forEach { block ->
                        processTextBlock(block)
                    }
                } else {
                    Log.d(TAG, "Nenhum texto encontrado na imagem")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Falha no reconhecimento de texto", e)
            }
    }

    private fun processTextBlock(block: Text.TextBlock) {
        val originalText = block.text
        val boundingBox = block.boundingBox ?: return
        
        // Verifica cache primeiro
        val cachedTranslation = translationCache[originalText]
        if (cachedTranslation != null) {
            overlayHelper.updateOverlayText(block.hashCode().toString(), boundingBox, cachedTranslation)
            return
        }
        
        // Se não estiver no cache, traduz
        translateText(originalText) { translation ->
            if (translation.isNotBlank()) {
                translationCache[originalText] = translation
                overlayHelper.updateOverlayText(block.hashCode().toString(), boundingBox, translation)
            }
        }
    }

    private fun translateText(text: String, onTranslated: (String) -> Unit) {
        selectedGemmaModel?.let { model ->
            val modelState = LlmChatModelHelper.getModelStateFlow(model.name).value
            
            if (modelState is com.google.ai.edge.gallery.ui.llmchat.ModelState.INITIALIZED) {
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val prompt = "Traduza o seguinte texto para $currentTargetLanguage, mantenha apenas a tradução: \"$text\""
                        
                        // Aqui você precisaria implementar a chamada real para o modelo
                        // Por exemplo: val translation = model.instance?.generateText(prompt)
                        // Por enquanto, usando um placeholder
                        val translation = "Tradução de: $text" // Placeholder
                        
                        serviceScope.launch(Dispatchers.Main) {
                            onTranslated(translation)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro na tradução", e)
                        serviceScope.launch(Dispatchers.Main) {
                            onTranslated("")
                        }
                    }
                }
            } else {
                Log.w(TAG, "Modelo não está inicializado para tradução")
                onTranslated("")
            }
        } ?: run {
            Log.w(TAG, "Nenhum modelo selecionado para tradução")
            onTranslated("")
        }
    }
}
