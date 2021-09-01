// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.perf

sealed class TestCheckResult {
    abstract infix fun and(other: TestCheckResult): TestCheckResult

    object Success : TestCheckResult() {
        override fun and(other: TestCheckResult) = when (other) {
            is Failure -> other
            Success -> Success
        }
    }

    data class Failure(val messages: List<String>) : TestCheckResult() {
        constructor(message: String) : this(listOf(message))

        override fun and(other: TestCheckResult) = when (other) {
            is Failure -> Failure(this.messages + other.messages)
            Success -> this
        }
    }
}

infix fun <T> ((T) -> TestCheckResult).and(other: ((T) -> TestCheckResult)): (T) -> TestCheckResult =
    { t -> this(t) and other(t) }