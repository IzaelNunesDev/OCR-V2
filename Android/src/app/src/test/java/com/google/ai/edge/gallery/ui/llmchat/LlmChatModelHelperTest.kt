package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import com.google.ai.edge.gallery.data.Model
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mockStatic
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Since LlmInference.createFromOptions is a static method and can throw exceptions
// or require native libraries, we might need Robolectric for a more stable environment
// or PowerMock/MockK for static mocking if direct mocking fails.
// For now, attempting with Robolectric and basic Mockito.
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33]) // Using SDK 33 for Robolectric
class LlmChatModelHelperTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockModel: Model

    @Mock
    private lateinit var mockLlmInference: LlmInference

    @Mock
    private lateinit var mockLlmInferenceSession: LlmInferenceSession

    private lateinit var closeable: AutoCloseable

    @Before
    fun setUp() {
        closeable = MockitoAnnotations.openMocks(this)
        // Stubbing model properties
        whenever(mockModel.name).doReturn("testModel")
        whenever(mockModel.getPath(any())).doReturn("dummy/path/model.task")
        // Default stubs for config values, can be overridden in specific tests
        whenever(mockModel.getIntConfigValue(any(), any())).doReturn(0)
        whenever(mockModel.getFloatConfigValue(any(), any())).doReturn(0f)
        whenever(mockModel.getStringConfigValue(any(), any())).doReturn("")
        whenever(mockModel.llmSupportImage).doReturn(false)
    }

    @After
    fun tearDown() {
        LlmChatModelHelper.cleanUp(mockModel) // Clean up any state associated with mockModel
        closeable.close()
    }

    @Test
    fun `getModelStateFlow returns NOT_INITIALIZED for new model`() = runTest {
        val modelName = "newModel"
        val stateFlow = LlmChatModelHelper.getModelStateFlow(modelName)
        assertEquals(ModelState.NOT_INITIALIZED, stateFlow.first())
    }

    @Test
    fun `getModelStateFlow returns same instance for same model name`() {
        val modelName = "consistentModel"
        val flow1 = LlmChatModelHelper.getModelStateFlow(modelName)
        val flow2 = LlmChatModelHelper.getModelStateFlow(modelName)
        assertSame(flow1, flow2)
    }

    @Test
    fun `initialize emits INITIALIZING then INITIALIZED on success`() = runTest {
        // This test is tricky due to static LlmInference.createFromOptions
        // We'll mock it to return our mockLlmInference instance
        // This requires org.mockito:mockito-inline if not using PowerMock
        // or if LlmInference is final/static methods are final.
        // Let's assume for now that it can be mocked via mockito-inline (often enabled by default in modern setups)
        // or that Robolectric's environment helps.

        mockStatic(LlmInference::class.java).use { mockedLlmInference ->
            mockedLlmInference.`when` { LlmInference.createFromOptions(any(), any()) }.thenReturn(mockLlmInference)
            mockStatic(LlmInferenceSession::class.java).use { mockedSession ->
                mockedSession.`when` { LlmInferenceSession.createFromOptions(any(), any()) }.thenReturn(mockLlmInferenceSession)

                val modelName = "successModel"
                whenever(mockModel.name).doReturn(modelName)
                val stateFlow = LlmChatModelHelper.getModelStateFlow(modelName)

                val states = mutableListOf<ModelState>()
                val job = launch {
                    stateFlow.collect { states.add(it) }
                }

                LlmChatModelHelper.initialize(mockContext, mockModel)

                // Check for expected states
                assertTrue(states.contains(ModelState.INITIALIZING))
                assertTrue(states.any { it is ModelState.INITIALIZED }) // Check if INITIALIZED is among the collected states
                assertNotNull(mockModel.instance)

                job.cancel() // Stop collecting
            }
        }
    }


    @Test
    fun `initialize emits ERROR when LlmInference creation fails`() = runTest {
        val errorMessage = "Failed to create LlmInference"
        mockStatic(LlmInference::class.java).use { mockedLlmInference ->
            mockedLlmInference.`when` { LlmInference.createFromOptions(any(), any()) }.thenThrow(RuntimeException(errorMessage))

            val modelName = "failModel"
            whenever(mockModel.name).doReturn(modelName)
            val stateFlow = LlmChatModelHelper.getModelStateFlow(modelName)

            val states = mutableListOf<ModelState>()
            val job = launch {
                stateFlow.collect { states.add(it) }
            }

            LlmChatModelHelper.initialize(mockContext, mockModel)
            
            assertTrue(states.contains(ModelState.INITIALIZING))
            val errorState = states.find { it is ModelState.ERROR }
            assertNotNull(errorState)
            assertEquals(errorMessage, (errorState as ModelState.ERROR).errorMessage)
            assertNull(mockModel.instance)

            job.cancel()
        }
    }
    
    @Test
    fun `initialize emits ERROR when LlmInferenceSession creation fails`() = runTest {
        val errorMessage = "Failed to create LlmInferenceSession"
        mockStatic(LlmInference::class.java).use { mockedLlmInference ->
            mockedLlmInference.`when` { LlmInference.createFromOptions(any(), any()) }.thenReturn(mockLlmInference)
            mockStatic(LlmInferenceSession::class.java).use { mockedSession ->
                 mockedSession.`when` { LlmInferenceSession.createFromOptions(any(), any()) }.thenThrow(RuntimeException(errorMessage))

                val modelName = "failSessionModel"
                whenever(mockModel.name).doReturn(modelName)
                val stateFlow = LlmChatModelHelper.getModelStateFlow(modelName)
    
                val states = mutableListOf<ModelState>()
                val job = launch {
                    stateFlow.collect { states.add(it) }
                }
    
                LlmChatModelHelper.initialize(mockContext, mockModel)
    
                assertTrue(states.contains(ModelState.INITIALIZING))
                val errorState = states.find { it is ModelState.ERROR }
                assertNotNull(errorState)
                assertTrue((errorState as ModelState.ERROR).errorMessage!!.contains(errorMessage)) // Mediapipe error message might be wrapped
                assertNull(mockModel.instance)
    
                job.cancel()
            }
        }
    }


    @Test
    fun `cleanUp sets state to NOT_INITIALIZED and nullifies instance`() = runTest {
        // First, "initialize" the model (simulated)
        mockStatic(LlmInference::class.java).use { mockedLlmInference ->
            mockedLlmInference.`when` { LlmInference.createFromOptions(any(), any()) }.thenReturn(mockLlmInference)
            mockStatic(LlmInferenceSession::class.java).use { mockedSession ->
                mockedSession.`when` { LlmInferenceSession.createFromOptions(any(), any()) }.thenReturn(mockLlmInferenceSession)
                
                val modelName = "cleanupModel"
                whenever(mockModel.name).doReturn(modelName)
                LlmChatModelHelper.initialize(mockContext, mockModel) // This should set instance
                assertNotNull(mockModel.instance) // Pre-condition
                assertEquals(ModelState.INITIALIZED, LlmChatModelHelper.getModelStateFlow(modelName).value) // Pre-condition
        
                // Now, call cleanUp
                LlmChatModelHelper.cleanUp(mockModel)
        
                assertEquals(ModelState.NOT_INITIALIZED, LlmChatModelHelper.getModelStateFlow(modelName).value)
                assertNull(mockModel.instance)
            }
        }
    }
}
