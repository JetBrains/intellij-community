// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools.sdkTools

import com.intellij.openapi.util.Key
import com.intellij.python.sdk.backend.PythonMockInterpreterKeys
import org.jetbrains.annotations.TestOnly

/**
 * The data of a mock SDK of a test, see [PythonMockSdk]. A mock SDK cannot run, so its facts come from here, see
 * [PythonMockInterpreterFactsStep].
 */
@TestOnly
object PythonMockSdkKeys {
  /** The `sys.path` of the mock SDK. Without it, the directory of its home path is its `sys.path`. */
  @JvmField
  val SYS_PATH: Key<List<String>> = PythonMockInterpreterKeys.SYS_PATH

  /** The version of the mock SDK, such as `Python 3.12`. Without it, the SDK keeps its own version. */
  @JvmField
  val VERSION: Key<String> = PythonMockInterpreterKeys.VERSION

  /** Marks an SDK as a mock that sets neither [SYS_PATH] nor [VERSION]. */
  @JvmField
  val MARKER: Key<Boolean> = PythonMockInterpreterKeys.MARKER

}
