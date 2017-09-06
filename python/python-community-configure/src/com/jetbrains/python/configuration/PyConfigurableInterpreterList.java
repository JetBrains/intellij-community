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
package com.jetbrains.python.configuration;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Manages the SDK model shared between PythonSdkConfigurable and PyActiveSdkConfigurable.
 *
 * @author yole
 */
public class PyConfigurableInterpreterList {
  private ProjectSdksModel myModel;

  public static PyConfigurableInterpreterList getInstance(Project project) {
    return ServiceManager.getService(project, PyConfigurableInterpreterList.class);
  }

  public ProjectSdksModel getModel() {
    if (myModel == null) {
      myModel = new ProjectSdksModel();
      myModel.reset(null);
    }
    return myModel;
  }

  public void disposeModel() {
    if (myModel != null) {
      myModel.disposeUIResources();
      myModel = null;
    }
  }

  public List<Sdk> getAllPythonSdks(@Nullable final Project project) {
    List<Sdk> result = new ArrayList<>();
    for (Sdk sdk : getModel().getSdks()) {
      if (sdk.getSdkType() instanceof PythonSdkType) {
        result.add(sdk);
      }
    }
    Collections.sort(result, new PyInterpreterComparator(project));
    addDetectedSdks(result);

    return result;
  }

  private void addDetectedSdks(@NotNull final List<Sdk> result) {
    final List<String> sdkHomes = new ArrayList<>();
    sdkHomes.addAll(VirtualEnvSdkFlavor.INSTANCE.suggestHomePaths());
    sdkHomes.addAll(CondaEnvSdkFlavor.INSTANCE.suggestHomePaths());
    for (PythonSdkFlavor flavor : PythonSdkFlavor.getApplicableFlavors()) {
      if (flavor instanceof VirtualEnvSdkFlavor || flavor instanceof CondaEnvSdkFlavor) continue;
      sdkHomes.addAll(flavor.suggestHomePaths());
    }
    Collections.sort(sdkHomes);
    for (String sdkHome : SdkConfigurationUtil.filterExistingPaths(PythonSdkType.getInstance(), sdkHomes, getModel().getSdks())) {
      result.add(new PyDetectedSdk(sdkHome));
    }
  }

  public List<Sdk> getAllPythonSdks() {
    return getAllPythonSdks(null);
  }

  private static class PyInterpreterComparator implements Comparator<Sdk> {
    @Nullable private final Project myProject;

    public PyInterpreterComparator(@Nullable final Project project) {
      myProject = project;
    }

    @Override
    public int compare(Sdk o1, Sdk o2) {
      if (!(o1.getSdkType() instanceof PythonSdkType) ||
          !(o2.getSdkType() instanceof PythonSdkType)) {
        return -Comparing.compare(o1.getName(), o2.getName());
      }

      final boolean isVEnv1 = PythonSdkType.isVirtualEnv(o1);
      final boolean isVEnv2 = PythonSdkType.isVirtualEnv(o2);
      final boolean isRemote1 = PySdkUtil.isRemote(o1);
      final boolean isRemote2 = PySdkUtil.isRemote(o2);

      if (isVEnv1) {
        if (isVEnv2) {
          if (myProject != null && associatedWithCurrent(o1, myProject)) {
            if (associatedWithCurrent(o2, myProject)) return compareSdk(o1, o2);
            return -1;
          }
          return compareSdk(o1, o2);
        }
        return -1;
      }
      if (isVEnv2) return 1;
      if (isRemote1) {
        if (isRemote2) {
          return compareSdk(o1, o2);
        }
        return 1;
      }
      if (isRemote2) return -1;

      return compareSdk(o1, o2);
    }

    private static int compareSdk(final Sdk o1, final Sdk o2) {
      final PythonSdkFlavor flavor1 = PythonSdkFlavor.getFlavor(o1);
      final PythonSdkFlavor flavor2 = PythonSdkFlavor.getFlavor(o2);
      final LanguageLevel level1 = flavor1 != null ? flavor1.getLanguageLevel(o1) : LanguageLevel.getDefault();
      final LanguageLevel level2 = flavor2 != null ? flavor2.getLanguageLevel(o2) : LanguageLevel.getDefault();
      final int compare = Comparing.compare(level1, level2);
      if (compare != 0) return -compare;
      return Comparing.compare(o1.getName(), o2.getName());
    }


    private static boolean associatedWithCurrent(Sdk o1, Project project) {
      final PythonSdkAdditionalData data = (PythonSdkAdditionalData)o1.getSdkAdditionalData();
      if (data != null) {
        final String path = data.getAssociatedProjectPath();
        final String projectBasePath = project.getBasePath();
        if (path != null && path.equals(projectBasePath)) {
          return true;
        }
      }
      return false;
    }
  }
}
