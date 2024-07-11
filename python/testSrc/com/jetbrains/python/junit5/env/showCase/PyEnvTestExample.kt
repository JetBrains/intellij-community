// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.env.showCase

import com.jetbrains.python.junit5.env.PyEnvTestCase
import com.jetbrains.python.junit5.env.PythonBinaryPath
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile


@PyEnvTestCase
class PyEnvTestExample {
  @Test
  fun checkPythonPath(@PythonBinaryPath python: Path) {
    assertTrue(python.exists(), "$python doesn't exist")
    assertTrue(python.isRegularFile(), "$python isn't file")
    assertTrue(python.isExecutable(), "$python isn't executable")
  }
}