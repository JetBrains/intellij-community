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
package com.jetbrains.python;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import com.intellij.util.SystemProperties;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author dcheryasov
 */
public class PythonSdkConfigurator implements DirectoryProjectConfigurator {

  public void configureProject(final Project project, @NotNull final VirtualFile baseDir, Ref<Module> moduleRef) {
    // it it a virtualenv?
    final PythonSdkType sdkType = PythonSdkType.getInstance();
    //find virtualEnv in project directory
    final List<String> candidates = new ArrayList<>();
    if (project == null) return;
    final VirtualFile rootDir = project.getBaseDir();
    if (rootDir != null)
      candidates.addAll(VirtualEnvSdkFlavor.findInDirectory(rootDir));
    if (!candidates.isEmpty()) {
      String filePath = candidates.get(0);
      if (StringUtil.startsWithChar(filePath, '~')) {
        final String home = SystemProperties.getUserHome();
        filePath = home + filePath.substring(1);
      }
      final Sdk virtualEnvSdk = SdkConfigurationUtil.createAndAddSDK(filePath, sdkType);
      if (virtualEnvSdk != null) {
        SdkConfigurationUtil.setDirectoryProjectSdk(project, virtualEnvSdk);
        SdkAdditionalData additionalData = virtualEnvSdk.getSdkAdditionalData();
        if (additionalData == null) {
          additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(virtualEnvSdk.getHomePath()));
          ((ProjectJdkImpl)virtualEnvSdk).setSdkAdditionalData(additionalData);
        }
        ((PythonSdkAdditionalData) additionalData).associateWithProject(project);
        return;
      }
      return;
    }

    
    final Sdk existingSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (existingSdk != null && existingSdk.getSdkType() == sdkType) return; // SdkConfigurationUtil does the same
    final File executableFile = PythonSdkType.findExecutableFile(new File(project.getBasePath(), "bin"), "python");
    if (executableFile != null) {
      final File virtualEnvRoot = PythonSdkType.getVirtualEnvRoot(executableFile.getPath());
      if (virtualEnvRoot != null) {
        // yes, an unknown virtualenv; set it up as SDK
        final Sdk sdk = SdkConfigurationUtil.createAndAddSDK(executableFile.getPath(), sdkType);
        if (sdk != null) {
          SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk);
          return;
        }
      }
    }
    // default
    SdkConfigurationUtil.configureDirectoryProjectSdk(project, new PreferredSdkComparator(), sdkType);
  }
}
