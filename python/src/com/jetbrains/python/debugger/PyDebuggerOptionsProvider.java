// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
@State(
  name = "PyDebuggerOptionsProvider",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  }
)
public class PyDebuggerOptionsProvider implements PersistentStateComponent<PyDebuggerOptionsProvider.State> {
  private final State myState = new State();

  @NotNull
  private final Project myProject;

  public PyDebuggerOptionsProvider(@NotNull Project project) {
    myProject = project;
  }

  public static PyDebuggerOptionsProvider getInstance(Project project) {
    return ServiceManager.getService(project, PyDebuggerOptionsProvider.class);
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState.myAttachToSubprocess = state.myAttachToSubprocess;
    myState.mySaveCallSignatures = state.mySaveCallSignatures;
    myState.mySupportGeventDebugging = state.mySupportGeventDebugging;
    myState.mySupportQtDebugging = state.mySupportQtDebugging;
    myState.myPyQtBackend = state.myPyQtBackend;
    myState.myAttachProcessFilter = state.myAttachProcessFilter;
  }

  public static class State {
    public boolean myAttachToSubprocess = true;
    public boolean mySaveCallSignatures = false;
    public boolean mySupportGeventDebugging = false;
    public boolean mySupportQtDebugging = true;
    public String myPyQtBackend = "Auto";
    public String myAttachProcessFilter = "python";
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

  public boolean isSupportQtDebugging() {
    return myState.mySupportQtDebugging;
  }

  public void setSupportQtDebugging(boolean supportQtDebugging) {
    myState.mySupportQtDebugging = supportQtDebugging;
  }

  public String getPyQtBackend() {
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

