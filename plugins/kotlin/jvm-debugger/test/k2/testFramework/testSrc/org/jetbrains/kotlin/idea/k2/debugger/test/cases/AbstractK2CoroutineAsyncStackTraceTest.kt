// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test.cases

import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.test.AbstractCoroutineAsyncStackTraceTest

abstract class AbstractK2IdeK1CodeCoroutineAsyncStackTraceTest : AbstractCoroutineAsyncStackTraceTest()

abstract class AbstractK2IdeK2CodeCoroutineAsyncStackTraceTest : AbstractK2IdeK1CodeCoroutineAsyncStackTraceTest() {
    override val compileWithK2: Boolean
        get() = true

    override fun lambdasGenerationScheme(): JvmClosureGenerationScheme {
        return JvmClosureGenerationScheme.INDY
    }
}