// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black

import com.intellij.openapi.vfs.VirtualFile

sealed class BlackFormattingRequest(val virtualFile: VirtualFile, val documentText: String) {

  class Fragment(virtualFile: VirtualFile, documentText: String, val lineRanges: List<IntRange>) : BlackFormattingRequest(virtualFile, documentText)

  class File(virtualFile: VirtualFile, documentText: String) : BlackFormattingRequest(virtualFile, documentText)
}