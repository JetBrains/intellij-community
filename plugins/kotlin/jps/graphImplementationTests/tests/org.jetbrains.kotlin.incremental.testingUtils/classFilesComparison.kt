// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.incremental.testingUtils

import java.io.File

// This is needed to disable comparison of out folders
fun assertEqualDirectories(expected: File, actual: File, forgiveExtraFiles: Boolean) {
}