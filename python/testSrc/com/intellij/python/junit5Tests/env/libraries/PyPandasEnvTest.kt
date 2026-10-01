// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.libraries

import com.intellij.idea.TestFor
import com.intellij.python.junit5Tests.framework.env.PyEnvTestCase
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyEnvCodeInsightTestCase
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Code insight with the real pandas package installed and without `pandas-stubs`.
 *
 * The `Timestamp.__new__` stub of pandas gives `ts_input` a union that has `str`, and the return type `Self | NaTType`.
 */
@PyEnvTestCase
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyPandasEnvTest : PyEnvCodeInsightTestCase() {

  /** The bytes argument is a control: it shows that the call check reads the parameter type from the pandas stub. */
  @Test
  @TestFor(issues = ["PY-53491"])
  fun `a string argument of Timestamp is not reported`() = test("""
    import pandas

    pandas.Timestamp("2022-03-17 00:00:00")
    pandas.Timestamp(b"2022")
    #                ^^^^^^^ WARNING Expected type 'integer[Any] | float | int | str | date | datetime | datetime64[Any]', got 'bytes' instead
  """)

  @Test
  @TestFor(issues = ["PY-53491"])
  fun `a Timestamp call is a Timestamp or NaT`() = test("""
    import pandas

    expr = pandas.Timestamp("2022-03-17 00:00:00")
    #      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ TYPE Timestamp | NaTType
  """)

  companion object {
    @JvmStatic
    @BeforeAll
    fun installPandas() {
      installPackages("pandas==3.0.2")
    }
  }
}
