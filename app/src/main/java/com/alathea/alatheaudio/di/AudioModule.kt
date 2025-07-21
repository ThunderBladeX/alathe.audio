package com.alathea.alatheaudio.di

import android.content.Context
import com.alathea.alatheaudio.jni.AudioEngineInterface
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides audio-related dependencies.
 * 
 * This module ensures that AudioEngineInterface is created as a singleton
 * to prevent lifecycle issues where Activity recreation could clash with
 * the long-running PlayerService that depends on persistent native state.
 * 
 * The audio engine is initialized once and shared between:
 * - PlayerService (for actual playback control)
 * - MainActivity/ViewModels (for UI state and settings)
 * - Any other components that need audio engine access
 */
@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    /**
     * Provides the singleton AudioEngineInterface instance.
     * 
     * This prevents the critical issue where Activity recreation would
     * create a new AudioEngineInterface() while PlayerService still
     * holds references to the old native engine state, leading to
     * crashes or unpredictable behavior.
     * 
     * The audio engine is initialized lazily - actual native initialization
     * happens in PlayerService.onCreate() when we have proper context
     * and can read user preferences for sample rate, buffer size, etc.
     */
    @Provides
    @Singleton
    fun provideAudioEngine(@ApplicationContext context: Context): AudioEngineInterface {
        return AudioEngineInterface()
    }
}
