package com.jetbrains.packagesearch.intellij.plugin

import kotlin.reflect.KProperty0

internal sealed class Assertion<out T>(val message: String?) {

    fun <V> failure(throwable: Throwable, message: String? = this.message): Assertion<V> =
        WithFailure(throwable, message)

    fun ifHasValue(assertion: (T) -> Unit) {
        if (this !is WithValue) return

        assertion(value)
    }

    fun <V> map(message: String? = this.message, transform: (T) -> V): Assertion<V> =
        when (this) {
            is WithFailure -> failure(throwable, message)
            is WithValue -> try {
                assertThat(transform(value), message)
            } catch (t: Throwable) {
                failure(t, message)
            }
        }

    abstract fun <A> assertThat(actual: A, message: String? = null): Assertion<A>

    class WithValue<T>(val value: T, message: String?) : Assertion<T>(message) {

        override fun <A> assertThat(actual: A, message: String?): Assertion<A> = WithValue(actual, message)
    }

    class WithFailure<T>(val throwable: Throwable, message: String?) : Assertion<T>(message) {

        override fun <A> assertThat(actual: A, message: String?): Assertion<A> = WithFailure(throwable, message)
    }
}

internal fun <T> assertThat(actual: T, message: String? = null): Assertion<T> =
    Assertion.WithValue(value = actual, message = message)

internal fun <T> assertThat(getter: KProperty0<T>, message: String? = null): Assertion<T> =
    assertThat(getter.get(), message ?: getter.name)

internal fun <T> assertThat(action: () -> T, message: String? = null): Assertion<Result<T>> =
    assertThat(runCatching { action() }, message)
