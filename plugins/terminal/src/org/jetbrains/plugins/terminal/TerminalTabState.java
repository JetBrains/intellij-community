// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TerminalTabState {
  @Attribute("tabName")
  public @Nls String myTabName;

  @Tag("shellCommand")
  @XCollection(elementName = "arg")
  public @Nullable List<String> myShellCommand;

  @Attribute("userDefinedTabTitle")
  public boolean myIsUserDefinedTabTitle;

  @Attribute("currentWorkingDirectory")
  public @NlsSafe String myWorkingDirectory;

  @Attribute("commandHistoryFileName")
  public String myCommandHistoryFileName;
}
