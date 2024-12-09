// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import java.nio.file.Path

/**
 * How do we get system python?
 */
sealed interface ObtainPythonStrategy {
  /**
   * Find it on the system. If no python found -- [confirmInstallation] and install
   */
  fun interface FindOnSystem : ObtainPythonStrategy {
    suspend fun confirmInstallation(): Boolean
  }

  /**
   * Only use [pythons] provided explicitly
   */
  data class UseThesePythons(val pythons: List<Pair<PythonSdkFlavor<*>, Collection<Path>>>) : ObtainPythonStrategy {
    init {
      assert(pythons.flatMap { it.second }.isNotEmpty()) { "When provided explicitly, pythons can't be empty" }
    }
  }
}