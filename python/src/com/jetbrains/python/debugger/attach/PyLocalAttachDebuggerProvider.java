// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.attach.LocalAttachHost;
import com.intellij.xdebugger.attach.XAttachDebugger;
import com.intellij.xdebugger.attach.XAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XAttachHost;
import com.intellij.xdebugger.attach.XAttachPresentationGroup;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.intellij.python.sdk.backend.PythonInterpreter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PyLocalAttachDebuggerProvider implements XAttachDebuggerProvider {
  private static final Key<List<XAttachDebugger>> DEBUGGERS_KEY = Key.create("PyLocalAttachDebuggerProvider.DEBUGGERS");

  @Override
  public @NotNull XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
    return PyLocalAttachGroup.INSTANCE;
  }

  @Override
  public boolean isAttachHostApplicable(@NotNull XAttachHost attachHost) {
    return attachHost instanceof LocalAttachHost;
  }

  /**
   * Creates a debugger for each local interpreter of the Python projects of {@code project}, see
   * {@link PyLocalAttachInterpretersKt#localAttachInterpreters}. The interpreter of the selected run configuration is first.
   *
   * @return list of debuggers for attaching to process
   */
  private static @NotNull List<XAttachDebugger> getAttachDebuggersForAllLocalSdks(@NotNull Project project) {
    PythonInterpreter selected = null;
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings != null) {
      RunConfiguration runConfiguration = settings.getConfiguration();
      if (runConfiguration instanceof AbstractPythonRunConfiguration) {
        selected = ((AbstractPythonRunConfiguration<?>)runConfiguration).getPythonInterpreter();
      }
    }

    final PythonInterpreter selectedInterpreter = selected;
    final List<XAttachDebugger> result = PyLocalAttachInterpretersKt.localAttachInterpreters(project)
      .stream()
      .filter(interpreter -> !interpreter.equals(selectedInterpreter))
      .map(PyLocalAttachDebugger::new)
      .collect(Collectors.toList());
    if (selectedInterpreter != null) {
      result.add(0, new PyLocalAttachDebugger(selectedInterpreter));
    }
    return result;
  }

  @Override
  public @NotNull List<? extends XAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                                        @NotNull XAttachHost attachHost,
                                                                        @NotNull ProcessInfo processInfo,
                                                                        @NotNull UserDataHolder contextHolder) {
    final String filter = PyDebuggerOptionsProvider.getInstance(project).getAttachProcessFilter();
    if (StringUtil.containsIgnoreCase(processInfo.getCommandLine(), filter)) {
      List<XAttachDebugger> result;

      if (processInfo.getExecutableCannonicalPath().isPresent() &&
          new File(processInfo.getExecutableCannonicalPath().get()).exists()) {
        result = new ArrayList<>(Arrays.asList(new PyLocalAttachDebugger(project, processInfo.getExecutableCannonicalPath().get())));
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
    private final @Nullable PythonInterpreter myInterpreter;
    private final @NotNull @NlsSafe String myName;

    PyLocalAttachDebugger(@NotNull PythonInterpreter interpreter) {
      myInterpreter = interpreter;
      myName = PyLocalAttachInterpretersKt.attachDebuggerName(interpreter);
    }

    /** A debugger for the process of the Python binary at {@code binaryPath}, with the interpreter of the project for it. */
    PyLocalAttachDebugger(@NotNull Project project, @NotNull String binaryPath) {
      myInterpreter = PyLocalAttachInterpretersKt.findLocalAttachInterpreter(project, binaryPath);
      myName = "Python Debugger";
    }

    @Override
    public @NotNull String getDebuggerDisplayName() {
      return myName;
    }

    @Override
    public void attachDebugSession(@NotNull Project project,
                                   @NotNull XAttachHost attachHost,
                                   @NotNull ProcessInfo processInfo) throws ExecutionException {
      PyAttachToProcessDebugRunner runner = new PyAttachToProcessDebugRunner(project, processInfo.getPid(), myInterpreter);
      runner.launch();
    }
  }
}
