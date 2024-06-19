// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.classic

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.terminal.classic.fixture.TestShellSession

class BasicShellTerminalIntegrationTest : BasePlatformTestCase() {
  fun testEchoAndClear() {
    if (!SystemInfo.isUnix) {
      return
    }
    val session = TestShellSession(project, testRootDisposable)
    session.executeCommand("_MY_FOO=test; echo -e \"1\\n2\\n\$_MY_FOO\"")
    session.awaitScreenLinesEndWith(listOf("1", "2", "test"), 10000)
    session.executeCommand("clear")
    session.awaitScreenLinesAre(emptyList(), 10000)
  }
}
