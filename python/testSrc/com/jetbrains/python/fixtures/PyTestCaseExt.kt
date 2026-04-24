// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures

import com.jetbrains.python.fixtures.PyTestCase.fixme

/**
 * see [PyTestCase.fixme]
 *
 * is an extension of [PyTestCase] for better completions
 * */
@Suppress("UnusedReceiverParameter")
inline fun <reified ExpectedError : Throwable> PyTestCase.fixme(
  comment: String,
  anticipatedMessage: String,
  crossinline test: () -> Unit,
) =
  fixme(comment, ExpectedError::class.java, anticipatedMessage) { test() }
