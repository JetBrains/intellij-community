/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.SystemInfo;

import java.io.File;

/**
 * @author traff
 */
@State(
  name = "TerminalOptionsProvider",
  storages = @Storage("terminal.xml")
)
public class TerminalOptionsProvider implements PersistentStateComponent<TerminalOptionsProvider.State> {
  private State myState = new State();

  public static TerminalOptionsProvider getInstance() {
    return ServiceManager.getService(TerminalOptionsProvider.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState.myShellPath = state.myShellPath;
    myState.myCloseSessionOnLogout = state.myCloseSessionOnLogout;
    myState.myReportMouse = state.myReportMouse;
    myState.mySoundBell = state.mySoundBell;
    myState.myTabName = state.myTabName;
    myState.myCopyOnSelection = state.myCopyOnSelection;
    myState.myPasteOnMiddleMouseButton = state.myPasteOnMiddleMouseButton;
    myState.myOverrideIdeShortcuts = state.myOverrideIdeShortcuts;
  }

  public boolean closeSessionOnLogout() {
    return myState.myCloseSessionOnLogout;
  }

  public boolean enableMouseReporting() {
    return myState.myReportMouse;
  }

  public boolean audibleBell() {
    return myState.mySoundBell;
  }

  public String getTabName() {
    return myState.myTabName;
  }

  public boolean overrideIdeShortcuts() {
    return myState.myOverrideIdeShortcuts;
  }

  public void setOverrideIdeShortcuts(boolean overrideIdeShortcuts) {
    myState.myOverrideIdeShortcuts = overrideIdeShortcuts;
  }

  public static class State {
    public String myShellPath = getDefaultShellPath();
    public String myTabName = "Local";
    public boolean myCloseSessionOnLogout = true;
    public boolean myReportMouse = true;
    public boolean mySoundBell = true;
    public boolean myCopyOnSelection = true;
    public boolean myPasteOnMiddleMouseButton = true;
    public boolean myOverrideIdeShortcuts = true;
  }

  public String getShellPath() {
    return myState.myShellPath;
  }

  private static String getDefaultShellPath() {
    String shell = System.getenv("SHELL");
    
    if (shell != null && new File(shell).canExecute()) {
      return shell;
    }
    
    if (SystemInfo.isUnix) {
      return "/bin/bash";
    }
    else {
      return "cmd.exe";
    }
  }

  public void setShellPath(String shellPath) {
    myState.myShellPath = shellPath;
  }

  public void setTabName(String tabName) {
    myState.myTabName = tabName;
  }

  public void setCloseSessionOnLogout(boolean closeSessionOnLogout) {
    myState.myCloseSessionOnLogout = closeSessionOnLogout;
  }

  public void setReportMouse(boolean reportMouse) {
    myState.myReportMouse = reportMouse;
  }

  public void setSoundBell(boolean soundBell) {
    myState.mySoundBell = soundBell;
  }

  public boolean copyOnSelection() {
    return myState.myCopyOnSelection;
  }

  public void setCopyOnSelection(boolean copyOnSelection) {
    myState.myCopyOnSelection = copyOnSelection;
  }

  public boolean pasteOnMiddleMouseButton() {
    return myState.myPasteOnMiddleMouseButton;
  }

  public void setPasteOnMiddleMouseButton(boolean pasteOnMiddleMouseButton) {
    myState.myPasteOnMiddleMouseButton = pasteOnMiddleMouseButton;
  }
}

