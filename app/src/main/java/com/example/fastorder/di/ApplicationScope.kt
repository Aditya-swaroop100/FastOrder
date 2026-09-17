package com.example.fastorder.di

import javax.inject.Qualifier

/**
 * Marks the process-lifetime [kotlinx.coroutines.CoroutineScope].
 *
 * A qualifier, not a bare `CoroutineScope` binding, because "which scope?" is
 * the question that matters: a repository handed a `viewModelScope` by mistake
 * loses its auth-state subscription the moment a screen leaves. Naming the
 * scope at the injection site makes that a compile-time choice rather than an
 * assumption.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

