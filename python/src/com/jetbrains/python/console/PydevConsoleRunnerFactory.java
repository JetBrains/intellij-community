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
  public PydevConsoleRunner createConsoleRunner(@NotNull Project project,
                                                @Nullable Module contextModule) {
    Pair<Sdk, Module> sdkAndModule = PydevConsoleRunner.findPythonSdkAndModule(project, contextModule);

    Module module = sdkAndModule.second;
    Sdk sdk = sdkAndModule.first;

    assert sdk != null;

    PathMapper pathMapper = PydevConsoleRunner.getPathMapper(project, sdk);

    String[] setupFragment;

    PyConsoleOptions.PyConsoleSettings settingsProvider = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
    Collection<String> pythonPath = PythonCommandLineState.collectPythonPath(module, settingsProvider.shouldAddContentRoots(),
                                                                             settingsProvider.shouldAddSourceRoots());

    if (pathMapper != null) {
      pythonPath = pathMapper.convertToRemote(pythonPath);
    }

    String customStartScript = settingsProvider.getCustomStartScript();

    if (customStartScript.trim().length() > 0) {
      customStartScript = "\n" + customStartScript;
    }

    String selfPathAppend = PydevConsoleRunner.constructPythonPathCommand(pythonPath, customStartScript);

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

    BuildoutFacet facet = null;
    if (module != null) {
      facet = BuildoutFacet.getInstance(module);
    }

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

    Map<String, String> envs = Maps.newHashMap(settingsProvider.getEnvs());
    String ipythonEnabled = PyConsoleOptions.getInstance(project).isIpythonEnabled() ? "True" : "False";
    envs.put(PythonEnvUtil.IPYTHONENABLE, ipythonEnabled);


    return createConsoleRunner(project, sdk, workingDir, envs, PyConsoleType.PYTHON, setupFragment);
  }

  protected PydevConsoleRunner createConsoleRunner(Project project,
                                                   Sdk sdk,
                                                   String workingDir,
                                                   Map<String, String> envs, PyConsoleType consoleType, String ... setupFragment) {
    return new PydevConsoleRunner(project, sdk, consoleType, workingDir, envs, setupFragment);
  }
}
