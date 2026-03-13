// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import com.intellij.util.containers.ContainerUtil;

import java.util.List;

@Service(Service.Level.PROJECT)
@State(
  name = "PyDebuggerOptionsProvider",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
@ApiStatus.Internal
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
    public boolean myRunDebuggerInServerMode = true;
    public int myDebuggerPort = 29781;
    public @NonNls String myAttachProcessFilter = "python";
    public int myEvaluationResponseTimeout = 60_000;
    public @NonNls String myDebuggerBackend = PyDebuggerBackend.PYDEVD.name();
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
    if (StringUtil.toLowerCase(PyBundle.messagePointer("python.debugger.qt.backend.auto").get()).equals(myState.myPyQtBackend)) {
      return "auto";
    }
    return myState.myPyQtBackend;
  }

  public void setPyQtBackend(String backend) {
    myState.myPyQtBackend = backend;
  }

  public boolean isRunDebuggerInServerMode() {
    return myState.myRunDebuggerInServerMode;
  }

  public void setRunDebuggerInServerMode(boolean runDebuggerInServerMode) {
    myState.myRunDebuggerInServerMode = runDebuggerInServerMode;
  }

  public int getDebuggerPort() {
    return myState.myDebuggerPort;
  }

  public void setDebuggerPort(int port) {
    myState.myDebuggerPort = port;
  }

  public String getAttachProcessFilter() {
    return myState.myAttachProcessFilter;
  }

  public void setAttachProcessFilter(String filter) {
    myState.myAttachProcessFilter = filter;
  }

  public int getEvaluationResponseTimeout() {
    return myState.myEvaluationResponseTimeout;
  }

  public void setEvaluationResponseTimeout(int timeout) {
    myState.myEvaluationResponseTimeout = timeout;
  }

  public @NotNull PyDebuggerBackend getSelectedBackend() {
    try {
      return PyDebuggerBackend.valueOf(myState.myDebuggerBackend);
    }
    catch (IllegalArgumentException e) {
      return PyDebuggerBackend.PYDEVD;
    }
  }

  public void setSelectedBackend(@NotNull PyDebuggerBackend backend) {
    myState.myDebuggerBackend = backend.name();
  }

  public static boolean hasActivePythonSessions(@NotNull Project project) {
    return ContainerUtil.exists(
      XDebuggerManager.getInstance(project).getDebugSessions(),
      session -> session.getRunProfile() instanceof AbstractPythonRunConfiguration
    );
  }

  /**
   * Switches to {@code newBackend} and restarts active Python debug sessions if any.
   */
  @ApiStatus.Internal
  public static void switchBackendWithRestart(@NotNull Project project, @NotNull PyDebuggerBackend newBackend) {
    PyDebuggerBackend oldBackend = getInstance(project).getSelectedBackend();
    getInstance(project).setSelectedBackend(newBackend);
    if (oldBackend != newBackend) {
      project.getMessageBus().syncPublisher(PyDebuggerBackendSwitchedListener.TOPIC).backendSwitched(project, oldBackend, newBackend);
    }
    restartAllPythonSessions(project);
  }

  public static void restartAllPythonSessions(@NotNull Project project) {
    List<XDebugSession> sessions = ContainerUtil.filter(
      XDebuggerManager.getInstance(project).getDebugSessions(),
      session -> session.getRunProfile() instanceof AbstractPythonRunConfiguration
    );

    for (XDebugSession session : sessions) {
      RunnerAndConfigurationSettings settings = session.getExecutionEnvironment() != null
                                               ? session.getExecutionEnvironment().getRunnerAndConfigurationSettings() : null;
      if (settings != null) {
        session.addSessionListener(new XDebugSessionListener() {
          @Override
          public void sessionStopped() {
            // sessionStopped fires on a non-EDT thread (stopAsync callback); dispatch to EDT for executeConfiguration
            ApplicationManager.getApplication().invokeLater(
              () -> ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
            );
          }
        });
      }
      session.stop();
    }
  }
}

