// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.env.systemPython.impl

import com.intellij.python.community.services.systemPython.impl.providers.collectPythonsInPaths
import com.intellij.testFramework.assertNothingLogged
import com.intellij.testFramework.common.timeoutRunBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.regex.Pattern

internal class CollectPythonNoErrorTest {
  @Test
  fun testNoError(@TempDir tempDir: Path) {
    assertNothingLogged(failOnWarn = true) {
      val anotherDir = tempDir.resolve("foo")
      timeoutRunBlocking {
        Assertions.assertThat(collectPythonsInPaths(listOf(tempDir, anotherDir), listOf(Pattern.compile(".+")))).isEmpty()
      }
    }
  }
}
