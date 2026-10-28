// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.helpersLocator

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Locates the bundled `.pyi` stubs.
 *
 * The stubs live in the `intellij.python.bundledPyiStubs` module. The module holds `typeshed` and `third_party` stubs.
 */
@ApiStatus.Internal
object PythonBundledPyiStubsLocator {
  private const val MODULE_NAME: String = "intellij.python.bundledPyiStubs"
  private const val RELATIVE_PATH: String = "/python/bundledPyiStubs"

  /** The directory of all bundled stubs: `typeshed` and `third_party` are in it. */
  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false /* PY-93235 */)
  fun root(): Path = findModuleRootByJarPath(PythonBundledPyiStubsLocator::class.java, MODULE_NAME, RELATIVE_PATH)

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false /* PY-93235 */)
  fun findPath(@NonNls resourceName: String): Path {
    val root = root()
    val resourcePath = root.resolve(resourceName)
    if (resourcePath.exists()) {
      return resourcePath
    }
    error("'$resourceName' does not exist under the bundled .pyi stub root '$root'")
  }
}
