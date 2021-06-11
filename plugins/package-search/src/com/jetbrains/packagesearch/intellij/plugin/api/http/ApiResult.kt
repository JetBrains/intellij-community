package com.jetbrains.packagesearch.intellij.plugin.api.http

internal sealed class ApiResult<T : Any> {

    data class Success<T : Any>(val result: T) : ApiResult<T>()

    data class Failure<T : Any>(val throwable: Throwable) : ApiResult<T>()

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this !is Success

    inline fun <V : Any> mapSuccess(action: (T) -> V) =
        if (isSuccess) {
            Success(action((this as Success<T>).result))
        } else {
            @Suppress("UNCHECKED_CAST")
            this as Failure<V>
        }

    inline fun onFailure(action: (Throwable) -> Unit) = apply {
        if (this is Failure<*>) action(throwable)
    }

    inline fun onSuccess(action: (T) -> Unit) = apply {
        if (this is Success<T>) action(result)
    }
}
