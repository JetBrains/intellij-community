// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Subsystems.Debugger
@Layers.Functional
@TestFor(issues = ["PY-70276"])
class PyInterpreterParametersOrderTest {
  @Test
  fun `the parameters of the IDE go before a wrapper script in the interpreter options`() {
    val configured = listOf("-u", "/opt/wrapper.py")
    val additional = listOf("-X", "pycache_prefix=/cache")

    val parameters = PythonCommandLineState.joinInterpreterParameters(configured, additional)

    assertThat(parameters).containsExactly("-X", "pycache_prefix=/cache", "-u", "/opt/wrapper.py")
  }
}
