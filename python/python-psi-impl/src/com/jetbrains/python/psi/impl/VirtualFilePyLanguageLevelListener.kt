// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import com.jetbrains.python.psi.LanguageLevel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface VirtualFilePyLanguageLevelListener {
  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<VirtualFilePyLanguageLevelListener> = Topic(VirtualFilePyLanguageLevelListener::class.java)
  }

  fun levelChanged(virtualFile: VirtualFile, newLevel: LanguageLevel)
}
