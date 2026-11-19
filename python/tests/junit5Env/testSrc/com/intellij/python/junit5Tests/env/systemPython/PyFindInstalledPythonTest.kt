// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython

import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.python.test.env.common.PredefinedPyEnvironments
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.findInstalledPython
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * After the IDE installs a Python, the installer looks for it with the system Python detection, not in a list of SDKs.
 */
@PyEnvTestCase(env = PredefinedPyEnvironments.VANILLA_3_12)
class PyFindInstalledPythonTest {
  @Test
  fun `the lookup finds the system Python of the language level`(): Unit = timeoutRunBlocking(10.minutes) {
    val expected = SystemPythonService().findSystemPythons(localEel, forceRefresh = true)
      .first { it.pythonInfo.languageLevel == LanguageLevel.PYTHON312 }

    assertThat(findInstalledPython(LanguageLevel.PYTHON312)).isEqualTo(expected.pythonBinary)
  }

  @Test
  fun `the lookup finds nothing for a language level that no system Python has`(): Unit = timeoutRunBlocking(10.minutes) {
    val levels = SystemPythonService().findSystemPythons(localEel, forceRefresh = true).map { it.pythonInfo.languageLevel }
    val missing = LanguageLevel.SUPPORTED_LEVELS.first { it !in levels }

    assertThat(findInstalledPython(missing)).isNull()
  }
}
