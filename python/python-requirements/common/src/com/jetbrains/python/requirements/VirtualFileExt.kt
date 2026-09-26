// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun VirtualFile.isRequirementsTxtFile(): Boolean {
  val sequence = nameSequence
  val ext = FileUtilRt.getExtension(sequence)
  if (ext != "txt" && ext != "in") return false
  val path = path
  return path.contains("/requirements/") || sequence.contains("requirements")
}
