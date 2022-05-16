// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console;

import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.console.completion.PydevConsoleElement;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.target.PySdkTargetPaths;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.target.PyTargetAwareAdditionalData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonUnbuffered;

public class PydevConsoleRunnerUtil {
  private PydevConsoleRunnerUtil() {
  }

  @Nullable
  public static PyRemotePathMapper getPathMapper(@NotNull Project project,
                                                 @Nullable Sdk sdk,
                                                 @NotNull PyConsoleOptions.PyConsoleSettings consoleSettings) {
    if (sdk == null) return null;
    SdkAdditionalData sdkAdditionalData = sdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PyTargetAwareAdditionalData) {
      return PySdkTargetPaths.getPathMapper(project, consoleSettings, (PyTargetAwareAdditionalData)sdkAdditionalData);
    }
    if (sdkAdditionalData instanceof PyRemoteSdkAdditionalDataBase) {
      return getPathMapper(project, consoleSettings, (PyRemoteSdkAdditionalDataBase)sdkAdditionalData);
    }
    return null;
  }

  @NotNull
  static PyRemotePathMapper getPathMapper(@NotNull Project project,
                                          @NotNull PyConsoleOptions.PyConsoleSettings consoleSettings,
                                          @NotNull PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData) {
    PyRemotePathMapper remotePathMapper = PythonRemoteInterpreterManager.appendBasicMappings(project, null, remoteSdkAdditionalData);
    PathMappingSettings mappingSettings = consoleSettings.getMappingSettings();
    if (mappingSettings != null) {
      remotePathMapper.addAll(mappingSettings.getPathMappings(), PyRemotePathMapper.PyPathMappingType.USER_DEFINED);
    }
    return remotePathMapper;
  }

  @NotNull
  static Pair<Sdk, Module> findPythonSdkAndModule(@NotNull Project project, @Nullable Module contextModule) {
    Sdk sdk = null;
    Module module = null;
    PyConsoleOptions.PyConsoleSettings settings = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
    String sdkHome = settings.getSdkHome();
    if (sdkHome != null) {
      sdk = PythonSdkUtil.findSdkByPath(sdkHome);
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
        if (PythonSdkUtil.findPythonSdk(module) != null) {
          sdk = PythonSdkUtil.findPythonSdk(module);
        }
      }
    }
    else if (contextModule != null) {
      if (module == null) {
        module = contextModule;
      }
      if (sdk == null) {
        sdk = PythonSdkUtil.findPythonSdk(module);
      }
    }

    if (sdk == null) {
      for (Module m : ModuleManager.getInstance(project).getModules()) {
        if (PythonSdkUtil.findPythonSdk(m) != null) {
          sdk = PythonSdkUtil.findPythonSdk(m);
          module = m;
          break;
        }
      }
    }
    if (sdk == null) {
      if (PythonSdkUtil.getAllSdks().size() > 0) {
        sdk = PythonSdkUtil.getAllSdks().get(0); //take any python sdk
      }
    }
    return Pair.create(sdk, module);
  }

  static @NotNull String constructPyPathAndWorkingDirCommand(@NotNull Collection<String> pythonPath,
                                                             @Nullable String workingDir,
                                                             @NotNull String command) {
    if (workingDir != null) {
      pythonPath.add(workingDir);
    }
    final String path = Joiner.on(", ").join(Collections2.transform(pythonPath,
                                                                    input -> "'" + input.replace("\\", "\\\\").replace("'", "\\'") + "'"));

    return command.replace(PydevConsoleRunnerImpl.WORKING_DIR_AND_PYTHON_PATHS, path);
  }

  public static @NotNull Map<String, String> addDefaultEnvironments(@NotNull Sdk sdk,
                                                                    @NotNull Map<String, String> envs,
                                                                    @NotNull Project project) {
    setCorrectStdOutEncoding(envs, project);

    PythonEnvUtil.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(sdk));
    return envs;
  }

  /**
   * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
   *
   * @param envs    map of envs to add variable
   * @param project current project
   */
  private static void setCorrectStdOutEncoding(@NotNull Map<String, String> envs, @NotNull Project project) {
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
  public static void setCorrectStdOutEncoding(@NotNull GeneralCommandLine commandLine, @NotNull Project project) {
    final Charset defaultCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    commandLine.setCharset(defaultCharset);
    setPythonIOEncoding(commandLine.getEnvironment(), defaultCharset.name());
  }

  public static boolean isInPydevConsole(@NotNull PsiElement element) {
    return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null || hasConsoleKey(element);
  }

  private static boolean hasConsoleKey(@NotNull PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return false;
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return false;
    Boolean inConsole = element.getContainingFile().getVirtualFile().getUserData(PythonConsoleView.CONSOLE_KEY);
    return inConsole != null && inConsole;
  }

  @Nullable
  public static PythonConsoleData getPythonConsoleData(@Nullable ASTNode element) {
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
  private static ConsoleCommunication getConsoleCommunication(@NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY) : null;
  }

  @Nullable
  public static Sdk getConsoleSdk(@NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(PydevConsoleRunner.CONSOLE_SDK) : null;
  }
}
