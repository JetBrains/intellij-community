// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.attach;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.eel.provider.EelPathDescriptorKt;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.xdebugger.attach.LocalAttachHost;
import com.intellij.xdebugger.attach.XAttachDebugger;
import com.intellij.xdebugger.attach.XAttachDebuggerProvider;
import com.intellij.xdebugger.attach.XAttachHost;
import com.intellij.xdebugger.attach.XAttachPresentationGroup;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.configuration.PyModuleInterpreterReader;
import com.jetbrains.python.debugger.PyDebuggerBackend;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.intellij.python.sdk.backend.NativePythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreter;
import com.intellij.python.sdk.backend.PythonInterpreterExtKt;
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
  private static final Key<Ref<PythonInterpreter>> ADAPTER_INTERPRETER_KEY = Key.create("PyLocalAttachDebuggerProvider.ADAPTER_INTERPRETER");

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
  private static @NotNull List<XAttachDebugger> getAttachDebuggersForAllLocalSdks(@NotNull Project project,
                                                                                 @Nullable PythonInterpreter adapterInterpreter) {
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
      .map(interpreter -> new PyLocalAttachDebugger(interpreter, adapterInterpreter))
      .collect(Collectors.toList());
    if (selectedInterpreter != null) {
      result.add(0, new PyLocalAttachDebugger(selectedInterpreter, adapterInterpreter));
    }
    return result;
  }

  /**
   * The interpreter the debugpy debug adapter runs on: the one the user chose for the project. The process to attach to
   * says nothing about it. It can be started anywhere, from a terminal outside the IDE included, and its interpreter is
   * often none the IDE knows.
   * <p>
   * In order: the interpreter of the selected run configuration, of the main Python project, and of each module. Null
   * when the project has no usable local interpreter.
   */
  private static @Nullable PythonInterpreter findAdapterInterpreter(@NotNull Project project) {
    List<PythonInterpreter> candidates = new ArrayList<>();
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings != null && settings.getConfiguration() instanceof AbstractPythonRunConfiguration<?> configuration) {
      candidates.add(configuration.getPythonInterpreter());
    }
    candidates.add(PyLocalAttachInterpretersKt.mainProjectInterpreterIfReady(project));
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      candidates.add(PyModuleInterpreterReader.findModuleInterpreterIfReady(module));
    }

    for (PythonInterpreter interpreter : candidates) {
      if (isLocal(interpreter) && PythonInterpreterExtKt.getPythonInfo(interpreter).getSuccessOrNull() != null) {
        return interpreter;
      }
    }
    return null;
  }

  /** The attach host is local, so the adapter cannot run on an interpreter of WSL, Docker or a target. */
  private static boolean isLocal(@Nullable PythonInterpreter interpreter) {
    return interpreter instanceof NativePythonInterpreter nativeInterpreter &&
           EelPathDescriptorKt.getEelDescriptor(nativeInterpreter.getPythonBinary()) == LocalEelDescriptor.INSTANCE;
  }

  /**
   * Only debugpy needs it, so pydevd, which the action has always attached with, pays nothing for it. Resolved once per
   * attach action, and not on attach, so all processes of the list get the same interpreter.
   */
  private static @Nullable PythonInterpreter resolveAdapterInterpreter(@NotNull Project project, @NotNull UserDataHolder contextHolder) {
    if (PyDebuggerOptionsProvider.getInstance(project).getSelectedBackend() != PyDebuggerBackend.DEBUGPY) return null;
    Ref<PythonInterpreter> resolved = contextHolder.getUserData(ADAPTER_INTERPRETER_KEY);
    if (resolved == null) {
      resolved = Ref.create(findAdapterInterpreter(project));
      contextHolder.putUserData(ADAPTER_INTERPRETER_KEY, resolved);
    }
    return resolved.get();
  }

  @Override
  public @NotNull List<? extends XAttachDebugger> getAvailableDebuggers(@NotNull Project project,
                                                                        @NotNull XAttachHost attachHost,
                                                                        @NotNull ProcessInfo processInfo,
                                                                        @NotNull UserDataHolder contextHolder) {
    final String filter = PyDebuggerOptionsProvider.getInstance(project).getAttachProcessFilter();
    if (StringUtil.containsIgnoreCase(processInfo.getCommandLine(), filter)) {
      List<XAttachDebugger> result;
      PythonInterpreter adapterInterpreter = resolveAdapterInterpreter(project, contextHolder);

      if (processInfo.getExecutableCannonicalPath().isPresent() &&
          new File(processInfo.getExecutableCannonicalPath().get()).exists()) {
        result = new ArrayList<>(Arrays.asList(new PyLocalAttachDebugger(project, processInfo.getExecutableCannonicalPath().get(), adapterInterpreter)));
      }
      else {
        result = contextHolder.getUserData(DEBUGGERS_KEY);
        if (result != null) return result;

        result = getAttachDebuggersForAllLocalSdks(project, adapterInterpreter);
        contextHolder.putUserData(DEBUGGERS_KEY, Collections.unmodifiableList(result));
      }

      return result;
    }
    return Collections.emptyList();
  }

  private static class PyLocalAttachDebugger implements XAttachDebugger {
    private final @Nullable PythonInterpreter myInterpreter;
    private final @NotNull @NlsSafe String myName;
    private final @Nullable PythonInterpreter myAdapterInterpreter;

    PyLocalAttachDebugger(@NotNull PythonInterpreter interpreter, @Nullable PythonInterpreter adapterInterpreter) {
      myAdapterInterpreter = adapterInterpreter;
      myInterpreter = interpreter;
      myName = PyLocalAttachInterpretersKt.attachDebuggerName(interpreter);
    }

    /** A debugger for the process of the Python binary at {@code binaryPath}, with the interpreter of the project for it. */
    PyLocalAttachDebugger(@NotNull Project project, @NotNull String binaryPath, @Nullable PythonInterpreter adapterInterpreter) {
      myAdapterInterpreter = adapterInterpreter;
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
      PyAttachToProcessBackend backend = PyAttachToProcessBackend.find(project, myAdapterInterpreter);
      if (backend == null) {
        throw new ExecutionException(PyBundle.message("debugger.attach.to.process.no.backend"));
      }
      backend.attach(project, new PyAttachToProcessTarget(processInfo.getPid(), myInterpreter, myAdapterInterpreter));
    }
  }
}
