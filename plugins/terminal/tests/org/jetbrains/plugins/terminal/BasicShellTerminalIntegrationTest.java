// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.terminal.fixture.TestShellSession;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class BasicShellTerminalIntegrationTest extends BasePlatformTestCase {

  public void testEchoAndClear() throws IOException, ExecutionException {
    if (!SystemInfo.isUnix) {
      return;
    }
    TestShellSession session = new TestShellSession(getProject(), getTestRootDisposable());
    session.executeCommand("_MY_FOO=test; echo -e \"1\\n2\\n$_MY_FOO\"");
    session.awaitScreenLinesEndWith(ContainerUtil.newArrayList("1", "2", "test"), 10000);
    session.executeCommand("clear");
    session.awaitScreenLinesAre(Collections.emptyList(), 10000);
  }
}
