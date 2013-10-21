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
package com.jetbrains.python.newProject;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.NewDirectoryProjectAction;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;

import java.util.List;

/**
 * User : catherine
 */
public class PythonNewDirectoryProjectAction extends NewDirectoryProjectAction {
  private Sdk mySdk;
  private boolean myInstallFramework;

  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    PythonNewDirectoryProjectDialog dlg = new PythonNewDirectoryProjectDialog(project);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
    mySdk = dlg.getSdk();
    myInstallFramework = dlg.installFramework();
    Project newProject = generateProject(project, dlg);
    if (newProject != null) {
      SdkConfigurationUtil.setDirectoryProjectSdk(newProject, mySdk);
      final List<Sdk> sdks = PythonSdkType.getAllSdks();
      for (Sdk sdk : sdks) {
        final SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
        if (additionalData instanceof PythonSdkAdditionalData) {
          ((PythonSdkAdditionalData) additionalData).reassociateWithCreatedProject(newProject);
        }
      }
    }
  }

  @Override
  protected Object showSettings(DirectoryProjectGenerator generator, VirtualFile baseDir)
                                                                  throws ProcessCanceledException {
    Object settings = super.showSettings(generator, baseDir);
    if (settings instanceof PyNewProjectSettings) {
      ((PyNewProjectSettings)settings).setSdk(mySdk);
      ((PyNewProjectSettings)settings).setInstallFramework(myInstallFramework);
    }
    return settings;
  }
}
