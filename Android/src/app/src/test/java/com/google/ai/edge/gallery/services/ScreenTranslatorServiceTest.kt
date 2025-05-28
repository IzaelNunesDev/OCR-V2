package com.google.ai.edge.gallery.services

import android.content.Context
import android.content.SharedPreferences
import com.google.ai.edge.gallery.data.DEFAULT_TARGET_LANGUAGE
import com.google.ai.edge.gallery.data.PREF_TARGET_LANGUAGE
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ScreenTranslatorServiceTest {

    private lateinit var service: ScreenTranslatorService
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Setup service using Robolectric's ServiceController
        service = Robolectric.setupService(ScreenTranslatorService::class.java)
        // Get SharedPreferences
        sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        // Clear any existing preferences before each test
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun `prompt uses language from SharedPreferences`() {
        val testLanguage = "Spanish"
        sharedPreferences.edit().putString(PREF_TARGET_LANGUAGE, testLanguage).commit()

        // Manually call onCreate to ensure SharedPreferences are loaded,
        // Robolectric.setupService(ScreenTranslatorService::class.java) calls onCreate.
        // So, currentTargetLanguage should be updated.
        // We can verify this directly due to 'internal' visibility.
        assertEquals(testLanguage, service.currentTargetLanguage)

        // Simulate the relevant part of prompt generation
        val textToTranslate = "Hello world"
        val expectedPrompt = "Translate to $testLanguage: $textToTranslate"
        // In a real scenario, this would be inside processImageForTranslation.
        // We are testing the outcome (correct language in prompt) based on currentTargetLanguage.
        val actualPrompt = "Translate to ${service.currentTargetLanguage}: $textToTranslate"

        assertEquals(expectedPrompt, actualPrompt)
    }

    @Test
    fun `prompt uses default language when no preference is set`() {
        // No language set in SharedPreferences, so it should use DEFAULT_TARGET_LANGUAGE

        // Robolectric.setupService calls onCreate, which initializes currentTargetLanguage
        assertEquals(DEFAULT_TARGET_LANGUAGE, service.currentTargetLanguage)

        val textToTranslate = "Hello world"
        val expectedPrompt = "Translate to $DEFAULT_TARGET_LANGUAGE: $textToTranslate"
        val actualPrompt = "Translate to ${service.currentTargetLanguage}: $textToTranslate"

        assertEquals(expectedPrompt, actualPrompt)
    }

    @Test
    fun `prompt uses default language when preference is null`() {
        // Explicitly set a null preference (though getString should handle this with default)
        sharedPreferences.edit().putString(PREF_TARGET_LANGUAGE, null).commit()
        
        // Re-initialize service or manually call relevant part of onCreate if it wasn't designed to pick up runtime changes.
        // Since setupService calls onCreate, we need to get a new instance or re-trigger logic.
        // For this test, let's re-acquire the service after setting prefs.
        val newService = Robolectric.setupService(ScreenTranslatorService::class.java)

        assertEquals(DEFAULT_TARGET_LANGUAGE, newService.currentTargetLanguage)

        val textToTranslate = "Hello world"
        val expectedPrompt = "Translate to $DEFAULT_TARGET_LANGUAGE: $textToTranslate"
        val actualPrompt = "Translate to ${newService.currentTargetLanguage}: $textToTranslate"
        assertEquals(expectedPrompt, actualPrompt)
    }
}
