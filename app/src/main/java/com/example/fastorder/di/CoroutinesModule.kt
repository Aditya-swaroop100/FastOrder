package com.example.fastorder.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Process-wide coroutine plumbing.
 *
 * Kept out of the feature modules on purpose: a scope that outlives every
 * screen is infrastructure, and more than one feature will eventually want it.
 * When `:core:common` becomes a Gradle module this file moves there unchanged.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    /**
     * Application-lifetime scope handed to repositories.
     *
     * [SupervisorJob] so one failed child cannot cancel the scope and take
     * long-lived subscriptions - the auth-state listener, notably - down with
     * it. [Dispatchers.Default] because the work here is callback plumbing and
     * mapping, not blocking I/O: the Firebase SDK does its own networking on
     * its own threads.
     *
     * Never cancelled. It is tied to the process, and the OS reclaims it.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

