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
package com.jetbrains.python.newProject;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.remote.RemoteSdkCredentials;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.RemoteProjectSettings;
import icons.PythonIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class PythonBaseProjectGenerator extends PythonProjectGenerator implements DirectoryProjectGenerator {
  @NotNull
  @Nls
  @Override
  public String getName() {
    return "Pure Python";
  }

  @Nullable
  @Override
  public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
    return null;
  }

  @Override
  @Nullable
  public JComponent getSettingsPanel(File baseDir) throws ProcessCanceledException {
    return null;
  }

  @Override
  public Object getProjectSettings() {
    return new PyNewProjectSettings();
  }

  @Nullable
  @Override
  public Icon getLogo() {
    return PythonIcons.Python.Python_logo;
  }

  @Override
  public void generateProject(@NotNull final Project project, @NotNull VirtualFile baseDir, final Object settings,
                              @NotNull final Module module) {
    if (settings instanceof RemoteProjectSettings) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      assert manager != null;
      manager.createDeployment(project, baseDir, (RemoteProjectSettings)settings,
                               (RemoteSdkCredentials)((RemoteProjectSettings)settings).getSdk().getSdkAdditionalData());
    }
    else if (settings instanceof PyNewProjectSettings) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          ModuleRootModificationUtil.setModuleSdk(module, ((PyNewProjectSettings)settings).getSdk());
        }
      });
    }
  }

  @NotNull
  @Override
  public ValidationResult validate(@NotNull String baseDirPath) {
    /*if (PythonSdkType.isRemote(myProjectAction.getSdk())) {
      if (PythonRemoteInterpreterManager.getInstance() == null) {
        return new ValidationResult(PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED);
      }
    }*/
    return ValidationResult.OK;
  }
}
