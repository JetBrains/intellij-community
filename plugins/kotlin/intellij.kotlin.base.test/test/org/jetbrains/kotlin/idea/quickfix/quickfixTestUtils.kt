// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import java.io.File

fun findInspectionFile(startDir: File): File? {
    return findInspectionFile(startDir, ".k2Inspection")
}

fun findInspectionFile(startDir: File, inspectionFileName: String): File? {
    var currentDir: File? = startDir
    while (currentDir != null) {
        val inspectionFile = File(currentDir, inspectionFileName)
        if (inspectionFile.exists()) {
            return inspectionFile
        }
        currentDir = currentDir.parentFile
    }
    return null
}