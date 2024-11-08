// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.Toml
import java.io.IOException

val LOGGER = Logger.getInstance("#com.jetbrains.python.sdk.uv")

internal suspend fun getPyProjectTomlForUv(virtualFile: VirtualFile): VirtualFile? =
  withContext(Dispatchers.IO) {
    readAction {
      try {
        Toml.parse(virtualFile.inputStream).getTable("tool.uv")?.let { virtualFile }
      }
      catch (e: IOException) {
        LOGGER.info(e)
        null
      }
    }
  }