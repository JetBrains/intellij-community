/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PathMapper;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PythonEnvUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PydevConsoleRunnerFactory extends PythonConsoleRunnerFactory {
  @Override
  @NotNull
  public PydevConsoleRunner createConsoleRunner(@NotNull Project project,
                                                @Nullable Module contextModule) {
    Pair<Sdk, Module> sdkAndModule = PydevConsoleRunner.findPythonSdkAndModule(project, contextModule);

    @Nullable Module module = sdkAndModule.second;
    Sdk sdk = sdkAndModule.first;

    assert sdk != null;

    PyConsoleOptions.PyConsoleSettings settingsProvider = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();

    PathMapper pathMapper = PydevConsoleRunner.getPathMapper(project, sdk, settingsProvider);

    String workingDir = getWorkingDir(project, module, pathMapper, settingsProvider);

    String[] setupFragment = createSetupFragment(module, workingDir, pathMapper, settingsProvider);

    Map<String, String> envs = Maps.newHashMap(settingsProvider.getEnvs());
    putIPythonEnvFlag(project, envs);

    Consumer<String> rerunAction = title -> {
      PydevConsoleRunner runner = createConsoleRunner(project, module);
      if (runner instanceof PydevConsoleRunnerImpl) {
        ((PydevConsoleRunnerImpl)runner).setConsoleTitle(title);
      }
      runner.run();
    };

    return createConsoleRunner(project, sdk, workingDir, envs, PyConsoleType.PYTHON, settingsProvider, rerunAction, setupFragment);
  }

  public static void putIPythonEnvFlag(@NotNull Project project, Map<String, String> envs) {
    String ipythonEnabled = PyConsoleOptions.getInstance(project).isIpythonEnabled() ? "True" : "False";
    envs.put(PythonEnvUtil.IPYTHONENABLE, ipythonEnabled);
  }

  @Nullable
  public static String getWorkingDir(@NotNull Project project,
                                     @Nullable Module module,
                                     @Nullable PathMapper pathMapper,
                                     PyConsoleOptions.PyConsoleSettings settingsProvider) {
    String workingDir = settingsProvider.getWorkingDirectory();
    if (StringUtil.isEmpty(workingDir)) {
      if (module != null && ModuleRootManager.getInstance(module).getContentRoots().length > 0) {
        workingDir = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
      }
      else {
        if (ModuleManager.getInstance(project).getModules().length > 0) {
          VirtualFile[] roots = ModuleRootManager.getInstance(ModuleManager.getInstance(project).getModules()[0]).getContentRoots();
          if (roots.length > 0) {
            workingDir = roots[0].getPath();
          }
        }
      }
    }

    if (pathMapper != null && workingDir != null) {
      workingDir = pathMapper.convertToRemote(workingDir);
    }

    return workingDir;
  }

  public static String[] createSetupFragment(@Nullable Module module,
                                             @Nullable String workingDir,
                                             @Nullable PathMapper pathMapper,
                                             PyConsoleOptions.PyConsoleSettings settingsProvider) {
    String customStartScript = settingsProvider.getCustomStartScript();
    if (customStartScript.trim().length() > 0) {
      customStartScript = "\n" + customStartScript;
    }
    Collection<String> pythonPath = PythonCommandLineState.collectPythonPath(module, settingsProvider.shouldAddContentRoots(),
                                                                             settingsProvider.shouldAddSourceRoots());
    if (pathMapper != null) {
      pythonPath = pathMapper.convertToRemote(pythonPath);
    }
    String selfPathAppend = PydevConsoleRunner.constructPyPathAndWorkingDirCommand(pythonPath, workingDir, customStartScript);

    BuildoutFacet facet = null;
    if (module != null) {
      facet = BuildoutFacet.getInstance(module);
    }
    String[] setupFragment;
    if (facet != null) {
      List<String> path = facet.getAdditionalPythonPath();
      if (pathMapper != null) {
        path = pathMapper.convertToRemote(path);
      }
      String prependStatement = facet.getPathPrependStatement(path);
      setupFragment = new String[]{prependStatement, selfPathAppend};
    }
    else {
      setupFragment = new String[]{selfPathAppend};
    }

    return setupFragment;
  }

  @NotNull
  protected PydevConsoleRunner createConsoleRunner(@NotNull Project project,
                                                   @NotNull Sdk sdk,
                                                   @Nullable String workingDir,
                                                   @NotNull Map<String, String> envs,
                                                   @NotNull PyConsoleType consoleType,
                                                   @NotNull PyConsoleOptions.PyConsoleSettings settingsProvider,
                                                   @NotNull Consumer<String> rerunAction,
                                                   @NotNull String... setupFragment) {
    return new PydevConsoleRunnerImpl(project, sdk, consoleType, workingDir, envs, settingsProvider, rerunAction, setupFragment);
  }
}
