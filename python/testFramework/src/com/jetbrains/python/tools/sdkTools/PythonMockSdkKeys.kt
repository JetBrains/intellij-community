// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools.sdkTools

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * The data of a mock SDK of a test, see [PythonMockSdk]. A mock SDK cannot run, so its facts come from here, see
 * [PythonMockInterpreterFactsStep].
 */
@TestOnly
object PythonMockSdkKeys {
  /** The `sys.path` of the mock SDK. Without it, the directory of its home path is its `sys.path`. */
  @JvmField
  val SYS_PATH: Key<List<String>> = Key.create("PY_MOCK_SYS_PATH_KEY")

  /** The version of the mock SDK, such as `Python 3.12`. Without it, the SDK keeps its own version. */
  @JvmField
  val VERSION: Key<String> = Key.create("PY_MOCK_PY_VERSION_KEY")

  /** Marks an SDK as a mock that sets neither [SYS_PATH] nor [VERSION]. */
  @JvmField
  val MARKER: Key<Boolean> = Key.create("MOCK_PY_MARKER_KEY")

  /** Whether [sdk] is a mock SDK of a test. */
  @JvmStatic
  fun isMock(sdk: Sdk): Boolean =
    sdk.getUserData(VERSION) != null || sdk.getUserData(SYS_PATH) != null || sdk.getUserData(MARKER) != null

  /** The `sys.path` of the mock [sdk]: its [SYS_PATH], else the directory of its home path. */
  @JvmStatic
  fun sysPath(sdk: Sdk): List<String> =
    sdk.getUserData(SYS_PATH) ?: listOf(Path.of(requireNotNull(sdk.homePath) { "No home path for $sdk" }).parent.toString())
}
