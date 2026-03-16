// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.internal.impl

import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.community.services.internal.impl.VanillaPythonWithPythonInfoImpl.Companion.concurrentLimit
import com.intellij.python.community.services.internal.impl.VanillaPythonWithPythonInfoImpl.Companion.createByPythonBinary
import com.intellij.python.community.services.shared.PythonInfoComparator
import com.intellij.python.community.services.shared.PythonWithPythonInfo
import com.intellij.python.community.services.shared.VanillaPythonWithPythonInfo
import com.jetbrains.python.PathShortener
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
class VanillaPythonWithPythonInfoImpl internal constructor(
  override val pythonBinary: PythonBinary,
  override val pythonInfo: PythonInfo,
) : VanillaPythonWithPythonInfo, Comparable<PythonWithPythonInfo> {

  companion object {

    private val concurrentLimit = Semaphore(permits = 4)

    /**
     * Like [createByPythonBinary] but runs in parallel up to [concurrentLimit]
     * @return python path -> python with language level sorted from highest to lowest.
     */
    suspend fun createByPythonBinaries(pythonBinaries: Collection<PythonBinary>): Collection<Pair<PythonBinary, PyResult<VanillaPythonWithPythonInfo>>> =
      coroutineScope {
        pythonBinaries.map {
          async {
            concurrentLimit.withPermit {
              Pair(it, createByPythonBinary(it))
            }
          }
        }.awaitAll()
      }.sortedBy { it.first }

    suspend fun createByPythonBinary(pythonBinary: PythonBinary): PyResult<VanillaPythonWithPythonInfoImpl> {
      val pythonInfo = pythonBinary.validatePythonAndGetInfo().getOr { return it }
      return Result.success(VanillaPythonWithPythonInfoImpl(pythonBinary, pythonInfo))
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VanillaPythonWithPythonInfoImpl

    return pythonBinary == other.pythonBinary
  }

  override fun hashCode(): Int {
    return pythonBinary.hashCode()
  }

  override fun toString(): String {
    return "$pythonBinary ($pythonInfo)"
  }

  override suspend fun getReadableName(): @Nls String {
    val pythonString = PathShortener.shorten(pythonBinary)
    return "$pythonString ($pythonInfo)"
  }

  override fun compareTo(other: PythonWithPythonInfo): Int = PythonInfoComparator.compare(this, other)
}