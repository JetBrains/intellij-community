// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * The data of a mock interpreter of a test. A test builds a mock interpreter from a mock SDK, and puts the data on that
 * SDK with these keys. A mock interpreter cannot run, so its facts come from here, see [mockVersionForTests] and
 * [mockSysPathForTests]. A test can change the data later. The next update of the interpreter reads it again.
 */
@TestOnly
@ApiStatus.Internal
object PythonMockInterpreterKeys {
  /** The `sys.path` of the mock interpreter. Without it, the directory of its binary is its `sys.path`. */
  @JvmField
  val SYS_PATH: Key<List<String>> = Key.create("PY_MOCK_SYS_PATH_KEY")

  /** The version of the mock interpreter, such as `Python 3.12`. Without it, the SDK keeps its own version. */
  @JvmField
  val VERSION: Key<String> = Key.create("PY_MOCK_PY_VERSION_KEY")

  /** Marks a mock interpreter that sets neither [SYS_PATH] nor [VERSION]. */
  @JvmField
  val MARKER: Key<Boolean> = Key.create("MOCK_PY_MARKER_KEY")
}

/** Whether this is a mock interpreter of a test, see [PythonMockInterpreterKeys]. */
@TestOnly
@ApiStatus.Internal
fun PythonInterpreter.isMockForTests(): Boolean =
  sdk.getUserData(PythonMockInterpreterKeys.VERSION) != null ||
  sdk.getUserData(PythonMockInterpreterKeys.SYS_PATH) != null ||
  sdk.getUserData(PythonMockInterpreterKeys.MARKER) != null

/** The version of this mock interpreter: its [PythonMockInterpreterKeys.VERSION], else the version of its SDK. */
@TestOnly
@ApiStatus.Internal
fun PythonInterpreter.mockVersionForTests(): String? = sdk.getUserData(PythonMockInterpreterKeys.VERSION) ?: sdk.versionString

/** The `sys.path` of this mock interpreter: its [PythonMockInterpreterKeys.SYS_PATH], else the directory of its binary. */
@TestOnly
@ApiStatus.Internal
fun PythonInterpreter.mockSysPathForTests(): List<String> =
  sdk.getUserData(PythonMockInterpreterKeys.SYS_PATH)
  ?: listOf(Path.of(requireNotNull(sdk.homePath) { "No home path for the mock interpreter $this" }).parent.toString())

/**
 * Makes [data] the SDK data of this interpreter, such as the data of a uv interpreter that gives it the uv flavor, and
 * returns the data it had. A test restores the previous data afterwards.
 */
@TestOnly
@ApiStatus.Internal
suspend fun PythonInterpreter.setSdkAdditionalDataForTests(data: SdkAdditionalData?): SdkAdditionalData? {
  val previous = sdk.sdkAdditionalData
  edtWriteAction {
    val modificator = sdk.sdkModificator
    modificator.sdkAdditionalData = data
    modificator.commitChanges()
  }
  return previous
}
