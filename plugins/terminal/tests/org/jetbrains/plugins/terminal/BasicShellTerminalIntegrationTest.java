// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.Collections;

public class BasicShellTerminalIntegrationTest extends BaseShellTerminalIntegrationTest {

  public void testEchoAndClear() throws IOException {
    if (!SystemInfo.isUnix) {
      return;
    }
    myWidget.executeCommand("_MY_FOO=test; echo -e \"1\n2\n3\n$_MY_FOO\"");
    myWatcher.awaitScreenLinesEndWith(ContainerUtil.newArrayList("1", "2", "3", "test"), 10000);
    myWidget.executeCommand("clear");
    myWatcher.awaitScreenLinesAre(Collections.emptyList(), 10000);
  }
}
