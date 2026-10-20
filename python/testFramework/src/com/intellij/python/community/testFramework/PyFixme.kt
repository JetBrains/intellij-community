// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.testFramework

import com.intellij.testFramework.TestLoggerFactory

/**
 * Runs [test], which asserts the correct behavior, and expects it to fail because of the known defect [comment].
 *
 * The failure must be an [expectedErrorClass] whose message contains [anticipatedMessage], the wrong result of today.
 * When [test] passes, [fixme] fails, so the fix of the defect must remove the [fixme] call.
 */
fun fixme(comment: String, expectedErrorClass: Class<out Throwable>, anticipatedMessage: String, test: Runnable) {
  try {
    test.run()
  }
  catch (failedError: Throwable) {
    val expected = expectedErrorClass.isInstance(failedError) ||
                   failedError is TestLoggerFactory.TestLoggerAssertionError && expectedErrorClass.isInstance(failedError.cause)
    if (!expected) throw failedError
    val message = failedError.message.orEmpty()
    if (message.contains(anticipatedMessage)) return
    throw AssertionError("Test $comment expected the incorrect error message '$anticipatedMessage' " +
                         "was not found in actual message '$message'", failedError)
  }
  throw AssertionError("Test $comment was previously failing and was suppressed, but now it passes")
}

/** See [fixme]. */
inline fun <reified ExpectedError : Throwable> fixme(comment: String, anticipatedMessage: String, crossinline test: () -> Unit) {
  fixme(comment, ExpectedError::class.java, anticipatedMessage) { test() }
}
