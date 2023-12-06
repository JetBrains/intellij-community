// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.arrangement;

import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.plugins.terminal.TerminalTabState;

import java.util.List;

public final class TerminalArrangementState {

  @XCollection()
  public List<TerminalTabState> myTabStates = new SmartList<>();

  @Property()
  public int mySelectedTabIndex;

  TerminalArrangementState() {}
}
