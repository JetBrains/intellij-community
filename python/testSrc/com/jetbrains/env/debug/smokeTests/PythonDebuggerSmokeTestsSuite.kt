// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.smokeTests

import com.jetbrains.env.debug.PythonDebuggerCythonSpeedupsTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
  PythonDebuggerCythonSpeedupsTest::class,
  PythonDebuggerRunsWithoutErrorsTest::class
)
class PythonDebuggerSmokeTestsSuite