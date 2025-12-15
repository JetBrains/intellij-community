// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.util.io.FileUtil
import java.io.File

fun loadTestConfiguration(testFile: File): JsonObject {
    val fileText = FileUtil.loadFile(testFile, true)

    return JsonParser.parseString(fileText) as JsonObject
}