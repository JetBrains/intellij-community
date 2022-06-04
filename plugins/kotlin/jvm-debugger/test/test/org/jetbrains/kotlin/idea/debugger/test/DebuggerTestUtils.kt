// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:JvmName("DebuggerTestUtils")

package org.jetbrains.kotlin.idea.debugger.test

import org.jetbrains.kotlin.test.KotlinRoot

@JvmField
val DEBUGGER_TESTDATA_PATH_BASE: String =
    KotlinRoot.DIR.resolve("jvm-debugger").resolve("test").resolve("testData").path
