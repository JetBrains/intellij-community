// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.attach.*;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PyLocalAttachDebuggerProvider implements XAttachDebuggerProvider {
  private static final Key<List<XAttachDebugger>> DEBUGGERS_KEY = Key.create("PyLocalAttachDebuggerProvider.DEBUGGERS");

  @NotNull
  @Override
  public XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
    return PyLocalAttachGroup.INSTANCE;
  }

  @Override
  public boolean isAttachHostApplicable(@NotNull XAttachHost attachHost) {
    return attachHost instanceof LocalAttachHost;
  }

  /**
   * Get all local Python Sdks, sort them by version and put the currently selected Sdk (if it exists) to the beginning.
   * Create a debuggers for attaching based on this list of Sdks.
   *
   * @param project
   * @return list of debuggers for attaching to process
   */
  @NotNull
  private static List<XAttachDebugger> getAttachDebuggersForAllLocalSdks(@NotNull Project project) {
    Sdk selected = null;
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings != null) {
      RunConfiguration runConfiguration = settings.getConfiguration();
      if (runConfiguration instanceof AbstractPythonRunConfiguration) {
        selected = ((AbstractPythonRunConfiguration<?>)runConfiguration).getSdk();
      }
    }

    final Sdk selectedSdk = selected;
    // most recent python version goes first
    final List<XAttachDebugger> result = PythonSdkUtil.getAllLocalCPythons()
      .stream()
      .filter(sdk -> sdk != selectedSdk)
      .filter(sdk -> !PythonSdkUtil.isInvalid(sdk))
      .sorted(PreferredSdkComparator.INSTANCE)
      .map(PyLocalAttachDebugger::new)
      .collect(Collectors.toList());
    if (selectedSdk != null) {
      result.add(0, new PyLocalAttachDebugger(selectedSdk));
    }
    return result;
  }

  @NotNull
  @Override
  public List<XAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                     @NotNull XAttachHost attachHost,
                                                     @NotNull ProcessInfo processInfo,
                                                     @NotNull UserDataHolder contextHolder) {
    final String filter = PyDebuggerOptionsProvider.getInstance(project).getAttachProcessFilter();
    if (StringUtil.containsIgnoreCase(processInfo.getCommandLine(), filter)) {
      List<XAttachDebugger> result;

      if (processInfo.getExecutableCannonicalPath().isPresent() &&
          new File(processInfo.getExecutableCannonicalPath().get()).exists()) {
        result = new ArrayList<>(Arrays.asList(new PyLocalAttachDebugger(processInfo.getExecutableCannonicalPath().get())));
      }
      else {
        result = contextHolder.getUserData(DEBUGGERS_KEY);
        if (result != null) return result;

        result = getAttachDebuggersForAllLocalSdks(project);
        contextHolder.putUserData(DEBUGGERS_KEY, Collections.unmodifiableList(result));
      }

      return result;
    }
    return Collections.emptyList();
  }

  private static class PyLocalAttachDebugger implements XAttachDebugger {
    private final String mySdkHome;
    @NotNull @NlsSafe private final String myName;

    PyLocalAttachDebugger(@NotNull Sdk sdk) {
      mySdkHome = sdk.getHomePath();
      myName = PythonSdkType.getInstance().getVersionString(sdk) + " (" + mySdkHome + ")";
    }

    PyLocalAttachDebugger(@NotNull String sdkHome) {
      mySdkHome = sdkHome;
      myName = "Python Debugger";
    }

    @NotNull
    @Override
    public String getDebuggerDisplayName() {
      return myName;
    }

    @Override
    public void attachDebugSession(@NotNull Project project,
                                   @NotNull XAttachHost attachHost,
                                   @NotNull ProcessInfo processInfo) throws ExecutionException {
      PyAttachToProcessDebugRunner runner = new PyAttachToProcessDebugRunner(project, processInfo.getPid(), mySdkHome);
      runner.launch();
    }
  }
}
