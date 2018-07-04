/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.sdk;

import com.intellij.ide.macro.Macro;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class InterpreterDirectoryMacro extends Macro {
  @Override
  public String getName() {
    return "PyInterpreterDirectory";
  }

  @Override
  public String getDescription() {
    return "The directory containing the Python interpreter selected for the project";
  }

  @Nullable
  @Override
  public String expand(DataContext dataContext) {
    Module module = LangDataKeys.MODULE.getData(dataContext);
    if (module == null) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) {
        return null;
      }
      Module[] modules = ModuleManager.getInstance(project).getModules();
      if (modules.length == 0) {
        return null;
      }
      module = modules[0];
    }
    Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk != null) {
      VirtualFile homeDir = sdk.getHomeDirectory();
      if (homeDir == null) {
        return null;
      }
      String path = PathUtil.getLocalPath(homeDir.getParent());
      if (path != null) {
        return FileUtil.toSystemDependentName(path);
      }
    }
    return null;
  }
}
