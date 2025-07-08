package com.jetbrains.python.psi.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import com.jetbrains.python.psi.LanguageLevel

interface VirtualFilePyLanguageLevelListener {
  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<VirtualFilePyLanguageLevelListener> = Topic(VirtualFilePyLanguageLevelListener::class.java)
  }

  fun levelChanged(virtualFile: VirtualFile, newLevel: LanguageLevel)
}
