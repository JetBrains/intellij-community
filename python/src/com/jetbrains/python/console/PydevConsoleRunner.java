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
package com.jetbrains.python.console;

import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonUnbuffered;

public interface PydevConsoleRunner {

  Key<ConsoleCommunication> CONSOLE_KEY = new Key<>("PYDEV_CONSOLE_KEY");
  Key<Sdk> CONSOLE_SDK = new Key<>("PYDEV_CONSOLE_SDK_KEY");

  interface ConsoleListener {
    void handleConsoleInitialized(LanguageConsoleView consoleView);
  }


  @Nullable
  static PyRemotePathMapper getPathMapper(@NotNull Project project,
                                          Sdk sdk,
                                          PyConsoleOptions.PyConsoleSettings consoleSettings) {
    if (PySdkUtil.isRemote(sdk)) {
      PythonRemoteInterpreterManager instance = PythonRemoteInterpreterManager.getInstance();
      if (instance != null) {
        PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData = (PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData();
        return getPathMapper(project, consoleSettings, instance, remoteSdkAdditionalData);
      }
    }
    return null;
  }

  @NotNull
  static PyRemotePathMapper getPathMapper(@NotNull Project project,
                                          PyConsoleOptions.PyConsoleSettings consoleSettings,
                                          PythonRemoteInterpreterManager instance,
                                          PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData) {
    //noinspection ConstantConditions
    PyRemotePathMapper remotePathMapper = instance.setupMappings(project, remoteSdkAdditionalData, null);
    PathMappingSettings mappingSettings = consoleSettings.getMappingSettings();
    remotePathMapper.addAll(mappingSettings.getPathMappings(), PyRemotePathMapper.PyPathMappingType.USER_DEFINED);
    return remotePathMapper;
  }

  @NotNull
  static Pair<Sdk, Module> findPythonSdkAndModule(@NotNull Project project, @Nullable Module contextModule) {
    Sdk sdk = null;
    Module module = null;
    PyConsoleOptions.PyConsoleSettings settings = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
    String sdkHome = settings.getSdkHome();
    if (sdkHome != null) {
      sdk = PythonSdkType.findSdkByPath(sdkHome);
      if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      else {
        module = contextModule;
        if (module == null && ModuleManager.getInstance(project).getModules().length > 0) {
          module = ModuleManager.getInstance(project).getModules()[0];
        }
      }
    }
    if (sdk == null && settings.isUseModuleSdk()) {
      if (contextModule != null) {
        module = contextModule;
      }
      else if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      if (module != null) {
        if (PythonSdkType.findPythonSdk(module) != null) {
          sdk = PythonSdkType.findPythonSdk(module);
        }
      }
    }
    else if (contextModule != null) {
      if (module == null) {
        module = contextModule;
      }
      if (sdk == null) {
        sdk = PythonSdkType.findPythonSdk(module);
      }
    }

    if (sdk == null) {
      for (Module m : ModuleManager.getInstance(project).getModules()) {
        if (PythonSdkType.findPythonSdk(m) != null) {
          sdk = PythonSdkType.findPythonSdk(m);
          module = m;
          break;
        }
      }
    }
    if (sdk == null) {
      if (PythonSdkType.getAllSdks().size() > 0) {
        //noinspection UnusedAssignment
        sdk = PythonSdkType.getAllSdks().get(0); //take any python sdk
      }
    }
    return Pair.create(sdk, module);
  }

  static String constructPyPathAndWorkingDirCommand(@NotNull Collection<String> pythonPath,
                                                    @Nullable String workingDir,
                                                    @NotNull String command) {
    if (workingDir != null) {
      pythonPath.add(workingDir);
    }
    final String path = Joiner.on(", ").join(Collections2.transform(pythonPath,
                                                                    input -> "'" + input.replace("\\", "\\\\").replace("'", "\\'") + "'"));

    return command.replace(PydevConsoleRunnerImpl.WORKING_DIR_AND_PYTHON_PATHS, path);
  }

  static Map<String, String> addDefaultEnvironments(Sdk sdk, Map<String, String> envs, @NotNull Project project) {
    setCorrectStdOutEncoding(envs, project);

    PythonSdkFlavor.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(sdk));
    return envs;
  }

  /**
   * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
   *
   * @param envs    map of envs to add variable
   * @param project current project
   */
  static void setCorrectStdOutEncoding(@NotNull Map<String, String> envs, @NotNull Project project) {
    final Charset defaultCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    final String encoding = defaultCharset.name();
    setPythonIOEncoding(setPythonUnbuffered(envs), encoding);
  }

  /**
   * Set command line charset as current project charset.
   * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
   *
   * @param commandLine command line
   * @param project     current project
   */
  static void setCorrectStdOutEncoding(@NotNull GeneralCommandLine commandLine, @NotNull Project project) {
    final Charset defaultCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    commandLine.setCharset(defaultCharset);
    setPythonIOEncoding(commandLine.getEnvironment(), defaultCharset.name());
  }

  static boolean isInPydevConsole(PsiElement element) {
    return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null;
  }

  static boolean isPythonConsole(@Nullable ASTNode element) {
    return getPythonConsoleData(element) != null;
  }

  @Nullable
  static PythonConsoleData getPythonConsoleData(@Nullable ASTNode element) {
    if (element == null || element.getPsi() == null || element.getPsi().getContainingFile() == null) {
      return null;
    }

    VirtualFile file = PydevConsoleRunnerImpl.getConsoleFile(element.getPsi().getContainingFile());

    if (file == null) {
      return null;
    }
    return file.getUserData(PyConsoleUtil.PYTHON_CONSOLE_DATA);
  }

  @Nullable
  static ConsoleCommunication getConsoleCommunication(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(CONSOLE_KEY) : null;
  }

  @Nullable
  static Sdk getConsoleSdk(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(CONSOLE_SDK) : null;
  }

  void open();

  void runSync();

  void run();

  PydevConsoleCommunication getPydevConsoleCommunication();

  void addConsoleListener(PydevConsoleRunnerImpl.ConsoleListener consoleListener);

  PythonConsoleExecuteActionHandler getConsoleExecuteActionHandler();

  PyConsoleProcessHandler getProcessHandler();

  PythonConsoleView getConsoleView();
}
