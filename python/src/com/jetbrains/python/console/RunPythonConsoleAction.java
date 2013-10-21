/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.remote.PyRemoteSdkData;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author oleg
 */
public class RunPythonConsoleAction extends AnAction implements DumbAware {

  public RunPythonConsoleAction() {
    super();
    getTemplatePresentation().setIcon(PythonIcons.Python.Python);
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(false);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      Pair<Sdk, Module> sdkAndModule = findPythonSdkAndModule(project, e.getData(LangDataKeys.MODULE));
      if (sdkAndModule.first != null) {
        e.getPresentation().setEnabled(true);
      }
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    runPythonConsole(project, e.getData(LangDataKeys.MODULE));
  }

  @NotNull
  public static PydevConsoleRunner runPythonConsole(Project project, Module contextModule) {
    assert project != null : "Project is null";

    FileDocumentManager.getInstance().saveAllDocuments();

    Pair<Sdk, Module> sdkAndModule = findPythonSdkAndModule(project, contextModule);

    Module module = sdkAndModule.second;
    Sdk sdk = sdkAndModule.first;

    assert sdk != null;

    PathMappingSettings mappingSettings = getMappings(project, sdk);

    String[] setupFragment;

    PyConsoleOptions.PyConsoleSettings settingsProvider = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
    Collection<String> pythonPath = PythonCommandLineState.collectPythonPath(module, settingsProvider.addContentRoots(),
                                                                             settingsProvider.addSourceRoots());

    if (mappingSettings != null) {
      pythonPath = mappingSettings.convertToRemote(pythonPath);
    }

    String selfPathAppend = constructPythonPathCommand(pythonPath);

    String customStartScript = settingsProvider.getCustomStartScript();

    if (customStartScript.trim().length() > 0) {
      selfPathAppend += "\n" + customStartScript.trim();
    }

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

    if (mappingSettings != null) {
      workingDir = mappingSettings.convertToRemote(workingDir);
    }

    BuildoutFacet facet = null;
    if (module != null) {
      facet = BuildoutFacet.getInstance(module);
    }

    if (facet != null) {
      List<String> path = facet.getAdditionalPythonPath();
      if (mappingSettings != null) {
        path = mappingSettings.convertToRemote(path);
      }
      String prependStatement = facet.getPathPrependStatement(path);
      setupFragment = new String[]{prependStatement, selfPathAppend};
    }
    else {
      setupFragment = new String[]{selfPathAppend};
    }

    return PydevConsoleRunner
      .createAndRun(project, sdk, PyConsoleType.PYTHON, workingDir, Maps.newHashMap(settingsProvider.getEnvs()), setupFragment);
  }

  public static PathMappingSettings getMappings(Project project, Sdk sdk) {
    PathMappingSettings mappingSettings = null;
    if (PySdkUtil.isRemote(sdk)) {
      PythonRemoteInterpreterManager instance = PythonRemoteInterpreterManager.getInstance();
      if (instance != null) {
        mappingSettings =
          instance.setupMappings(project, (PyRemoteSdkData)sdk.getSdkAdditionalData(), null);
      }
    }
    return mappingSettings;
  }

  @NotNull
  private static Pair<Sdk, Module> findPythonSdkAndModule(Project project, Module contextModule) {
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

  public static String constructPythonPathCommand(Collection<String> pythonPath) {
    final String path = Joiner.on(", ").join(Collections2.transform(pythonPath, new Function<String, String>() {
      @Override
      public String apply(String input) {
        return "'" + input.replace("\\", "\\\\") + "'";
      }
    }));

    return "sys.path.extend([" + path + "])";
  }
}
