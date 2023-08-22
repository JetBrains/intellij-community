// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@State(
  name = "PyDebuggerOptionsProvider",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public final class PyDebuggerOptionsProvider implements PersistentStateComponent<PyDebuggerOptionsProvider.State> {
  private @NotNull State myState = new State();

  public static PyDebuggerOptionsProvider getInstance(Project project) {
    return project.getService(PyDebuggerOptionsProvider.class);
  }

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static class State {
    public boolean myAttachToSubprocess = true;
    public boolean mySaveCallSignatures = false;
    public boolean mySupportGeventDebugging = false;
    public boolean myDropIntoDebuggerOnFailedTests = false;
    public boolean mySupportQtDebugging = true;
    public @NonNls String myPyQtBackend = "auto";
    public @NonNls String myAttachProcessFilter = "python";
  }


  public boolean isAttachToSubprocess() {
    return myState.myAttachToSubprocess;
  }

  public void setAttachToSubprocess(boolean attachToSubprocess) {
    myState.myAttachToSubprocess = attachToSubprocess;
  }

  public boolean isSaveCallSignatures() {
    return myState.mySaveCallSignatures;
  }

  public void setSaveCallSignatures(boolean saveCallSignatures) {
    myState.mySaveCallSignatures = saveCallSignatures;
  }

  public boolean isSupportGeventDebugging() {
    return myState.mySupportGeventDebugging;
  }

  public void setSupportGeventDebugging(boolean supportGeventDebugging) {
    myState.mySupportGeventDebugging = supportGeventDebugging;
  }

  public boolean isDropIntoDebuggerOnFailedTest() {
    return myState.myDropIntoDebuggerOnFailedTests;
  }

  public void setDropIntoDebuggerOnFailedTest(boolean dropIntoDebuggerOnFailedTest) {
    myState.myDropIntoDebuggerOnFailedTests = dropIntoDebuggerOnFailedTest;
  }

  public boolean isSupportQtDebugging() {
    return myState.mySupportQtDebugging;
  }

  public void setSupportQtDebugging(boolean supportQtDebugging) {
    myState.mySupportQtDebugging = supportQtDebugging;
  }

  public String getPyQtBackend() {
    if (StringUtil.toLowerCase(PyBundle.messagePointer("python.debugger.qt.backend.auto").get()).equals(myState.myPyQtBackend))
      return "auto";
    return myState.myPyQtBackend;
  }

  public void setPyQtBackend(String backend) {
    myState.myPyQtBackend = backend;
  }

  public String getAttachProcessFilter() {
    return myState.myAttachProcessFilter;
  }

  public void setAttachProcessFilter(String filter) {
    myState.myAttachProcessFilter = filter;
  }
}

