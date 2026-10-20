// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCustomDataProvider
import com.intellij.util.messages.impl.subscribeAsFlow
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher
import com.jetbrains.python.psi.impl.VirtualFilePyLanguageLevelListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class PyLangLevelVirtualFileCustomDataProvider : VirtualFileCustomDataProvider<LanguageLevelHolder> {
  override val id: String = "virtualFilePyLangLevel"

  override val dataType: KType
    get() = typeOf<LanguageLevelHolder>()

  override fun getValues(project: Project, virtualFile: VirtualFile): Flow<LanguageLevelHolder> {
    return project.messageBus.subscribeAsFlow(VirtualFilePyLanguageLevelListener.TOPIC) {
      val level = readAction { PythonLanguageLevelPusher.getEffectiveLanguageLevel(project, virtualFile) }
      trySend(level)

      val vFile = virtualFile
      object : VirtualFilePyLanguageLevelListener {
        override fun levelChanged(virtualFile: VirtualFile, newLevel: LanguageLevel) {
          if (vFile == virtualFile) {
            trySend(newLevel)
          }
        }
      }
    }.map { LanguageLevelHolder(it) }
  }
}