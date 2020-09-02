// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nls;

public class TerminalTabState {
  @Attribute("tabName")
  public @Nls String myTabName;

  @Attribute("currentWorkingDirectory")
  public @NlsSafe String myWorkingDirectory;

  @Attribute("commandHistoryFileName")
  public String myCommandHistoryFileName;
}
