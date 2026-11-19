// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.intellij.python.pytools.common.FusId
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.python.sdk.backend.asUnavailableItem
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The row of an interpreter that an interpreter library entry names while its environment is gone: it is named like the
 * interpreter, flagged `[invalid]`, and its reason names the environment and its manager. An env ref is a path for some
 * managers and a name for others, so the row reads it as neither.
 */
@Subsystems.Interpreters
@Layers.Functional
@TestApplication
class PyUnavailableInterpreterItemTest {
  @Test
  fun `the row names the environment and its manager`(@TempDir projectDir: Path) {
    val key = PythonInterpreterKey(projectDir, PyInterpreterRef(PyInterpreterRef.Mode.Native, FusId("hatch"), PyEnvRef("test")))

    val item = key.asUnavailableItem()

    assertThat(item.ref).isEqualTo(key.ref)
    assertThat(item.name).isEqualTo("test")
    assertThat(item.description).isEqualTo("test")
    assertThat(item.problem?.marker).isEqualTo(PySdkBundle.message("python.sdk.problem.invalid"))
    assertThat(item.problem?.reason).isEqualTo(PySdkBundle.message("python.sdk.problem.environment.gone", "test", "hatch"))
  }
}
