/*
 * Copyright 2000-2022 JetBrains s.r.o. and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.idea.packagesearch.http

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

    fun getOrNull(): T? = when (this) {
        is Failure -> null
        is Success -> result
    }
}
