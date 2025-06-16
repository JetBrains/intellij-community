// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile

internal object RequirementsUtil {
  fun isRequirementsFile(file: VirtualFile): Boolean {
    val sequence = file.nameSequence
    val ext = FileUtilRt.getExtension(sequence)
    if (ext != "txt" && ext != "in") return false
    val path = file.path
    return path.contains("/requirements/")
  }
}