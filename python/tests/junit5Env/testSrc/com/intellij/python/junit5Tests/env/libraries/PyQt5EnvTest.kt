// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.libraries

import com.intellij.idea.TestFor
import com.intellij.openapi.util.SystemInfo
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.intellij.util.system.CpuArch
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyEnvCodeInsightTestCase
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Code insight with the real PyQt5 package installed. PyQt5 ships `.pyi` stubs.
 *
 * A signal such as `QAbstractButton.clicked` is a `ClassVar[pyqtSignal]`, and `pyqtSignal.__get__` has one overload
 * for the class and one for an instance. Only `pyqtBoundSignal` has `connect`.
 *
 * The class skips its tests on a platform without `PyQt5-Qt5` wheels, such as Linux on ARM64.
 */
@PyEnvTestCase
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyQt5EnvTest : PyEnvCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-24183"])
  fun `a signal read from a widget instance is a bound signal`() = test("""
    from PyQt5.QtWidgets import QPushButton

    button = QPushButton("OK")
    expr = button.clicked
    #      ^^^^^^^^^^^^^^ TYPE pyqtSignal FIXME pyqtBoundSignal
  """)

  @Test
  @TestFor(issues = ["PY-24183"])
  fun `a signal read from a widget class is an unbound signal`() = test("""
    from PyQt5.QtWidgets import QPushButton

    expr = QPushButton.clicked
    #      ^^^^^^^^^^^^^^^^^^^ TYPE pyqtSignal
  """)

  @Test
  @TestFor(issues = ["PY-24183"])
  fun `connect on a signal of a widget instance resolves`() = test("""
    from PyQt5.QtWidgets import QPushButton

    button = QPushButton("OK")
    button.clicked.connect(lambda: print("clicked"))
    #              ^^^^^^^ WARNING Unresolved attribute reference 'connect' for class 'pyqtSignal' FIXME
  """)

  @Test
  @TestFor(issues = ["PY-24183"])
  fun `a signal read from a QObject instance is a bound signal`() = test("""
    from PyQt5.QtCore import QObject

    def f(o: QObject):
        expr = o.destroyed
    #          ^^^^^^^^^^^ TYPE pyqtBoundSignal
  """)

  companion object {
    @JvmStatic
    @BeforeAll
    fun installPyQt5() {
      assumeTrue(SystemInfo.isMac || CpuArch.isIntel64(), "PyQt5-Qt5 has wheels for macOS and for x86-64 Linux and Windows only")
      installPackages("PyQt5==5.15.11")
    }
  }
}
