// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.internal.impl

import com.intellij.platform.eel.fs.pathOs
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelApi
import com.intellij.python.community.services.shared.PythonWithLanguageLevel
import com.jetbrains.python.LocalizedErrorString
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.validatePythonAndGetVersion
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

@Internal
class PythonWithLanguageLevelImpl internal constructor(
  override val pythonBinary: PythonBinary,
  override val languageLevel: LanguageLevel,
) : PythonWithLanguageLevel, Comparable<PythonWithLanguageLevelImpl> {
  companion object {
    suspend fun createByPythonBinary(pythonBinary: PythonBinary): Result<PythonWithLanguageLevelImpl, LocalizedErrorString> {
      val languageLevel = pythonBinary.validatePythonAndGetVersion().getOr { return it }
      return Result.success(PythonWithLanguageLevelImpl(pythonBinary, languageLevel))
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PythonWithLanguageLevelImpl

    return pythonBinary == other.pythonBinary
  }

  override fun hashCode(): Int {
    return pythonBinary.hashCode()
  }

  override fun toString(): String {
    return "$pythonBinary ($languageLevel)"
  }

  override suspend fun getReadableName(): @Nls String {
    val eelApi = pythonBinary.getEelApi()
    val home = eelApi.userInfo.home.asNioPath()
    val separator = when (eelApi.fs.pathOs) {
      EelPath.OS.WINDOWS -> "\\"
      EelPath.OS.UNIX -> "/"
    }
    val pythonString = (if (pythonBinary.startsWith(home)) "~$separator" + pythonBinary.relativeTo(home).pathString
    else pythonBinary.pathString)
    return "$pythonString ($languageLevel)"
  }

  // TODO: DOC backward
  override fun compareTo(other: PythonWithLanguageLevelImpl): Int = other.languageLevel.compareTo(languageLevel)
}