// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.internal.impl

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl.Companion.concurrentLimit
import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl.Companion.createByPythonBinary
import com.intellij.python.community.services.shared.PythonWithLanguageLevel
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.validatePythonAndGetVersion
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

@Internal
class PythonWithLanguageLevelImpl internal constructor(
  override val pythonBinary: PythonBinary,
  override val languageLevel: LanguageLevel,
) : PythonWithLanguageLevel, Comparable<PythonWithLanguageLevel> {
  companion object {

    private val concurrentLimit = Semaphore(permits = 4)

    /**
     * Like [createByPythonBinary] but runs in parallel up to [concurrentLimit]
     * @return python path -> python with language level sorted from highest to lowest.
     */
    suspend fun createByPythonBinaries(pythonBinaries: Collection<PythonBinary>): Collection<Pair<PythonBinary, Result<PythonWithLanguageLevel, @Nls String>>> =
      coroutineScope {
        pythonBinaries.map {
          async {
            concurrentLimit.withPermit {
              Pair(it, createByPythonBinary(it))
            }
          }
        }.awaitAll()
      }.sortedBy { it.first }

    suspend fun createByPythonBinary(pythonBinary: PythonBinary): Result<PythonWithLanguageLevelImpl, @Nls String> {
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
    val eelApi = pythonBinary.getEelDescriptor().upgrade()
    val home = eelApi.userInfo.home.asNioPath()
    val separator = when (eelApi.platform) {
      is EelPlatform.Windows -> "\\"
      is EelPlatform.Posix -> "/"
    }
    val pythonString = (if (pythonBinary.startsWith(home)) "~$separator" + pythonBinary.relativeTo(home).pathString
    else pythonBinary.pathString)
    return "$pythonString ($languageLevel)"
  }

  // Backward: first python is the highest
  override fun compareTo(other: PythonWithLanguageLevel): Int = languageLevel.compareTo(other.languageLevel) * -1
}