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
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * Manages the SDK model shared between PythonSdkConfigurable and PyActiveSdkConfigurable.
 *
 * @author yole
 */
public class PyConfigurableInterpreterList {
  private ProjectSdksModel myModel;
  private JComboBox mySdkCombo;

  public void setSdkCombo(final JComboBox sdkCombo) {
    mySdkCombo = sdkCombo;
  }

  public void setSelectedSdk(final Sdk selectedSdk) {
    if (mySdkCombo != null)
      mySdkCombo.setSelectedItem(selectedSdk);
  }

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
    List<Sdk> result = new ArrayList<Sdk>();
    for (Sdk sdk : getModel().getSdks()) {
      if (sdk.getSdkType() instanceof PythonSdkType) {
        result.add(sdk);
      }
    }

    Collections.sort(result, new Comparator<Sdk>() {
      @Override
      public int compare(Sdk o1, Sdk o2) {
        if (!(o1.getSdkType() instanceof PythonSdkType) ||
            !(o2.getSdkType() instanceof PythonSdkType))
          return -Comparing.compare(o1.getName(), o2.getName());

        final boolean isVEnv1 = PythonSdkType.isVirtualEnv(o1);
        final boolean isVEnv2 = PythonSdkType.isVirtualEnv(o2);
        final boolean isRemote1 = PySdkUtil.isRemote(o1);
        final boolean isRemote2 = PySdkUtil.isRemote(o2);
        final PythonSdkFlavor flavor1 = PythonSdkFlavor.getFlavor(o1);
        final PythonSdkFlavor flavor2 = PythonSdkFlavor.getFlavor(o2);
        final LanguageLevel level1 = flavor1 != null ? flavor1.getLanguageLevel(o1) : LanguageLevel.getDefault();
        final LanguageLevel level2 = flavor2 != null ? flavor2.getLanguageLevel(o2) : LanguageLevel.getDefault();

        if (isVEnv1) {
          if (project != null && associatedWithCurrent(o1, project)) return -1;
          if (isVEnv2) {
            final int compare = Comparing.compare(level1, level2);
            if (compare != 0) return -compare;
            return Comparing.compare(o1.getName(), o2.getName());
          }
          return -1;
        }
        if (isVEnv2) {
          return 1;
        }
        if (isRemote1) return 1;
        if (isRemote2) return -1;

        final int compare = Comparing.compare(level1, level2);
        if (compare != 0) return -compare;
        return Comparing.compare(o1.getName(), o2.getName());
      }
    });

    final Collection<String> sdkHomes = new ArrayList<String>();
    sdkHomes.addAll(VirtualEnvSdkFlavor.INSTANCE.suggestHomePaths());
    for (PythonSdkFlavor flavor : PythonSdkFlavor.getApplicableFlavors()) {
      if (flavor instanceof VirtualEnvSdkFlavor) continue;
      sdkHomes.addAll(flavor.suggestHomePaths());
    }

    for (String sdkHome : SdkConfigurationUtil.filterExistingPaths(PythonSdkType.getInstance(), sdkHomes, getModel().getSdks())) {
      result.add(new PyDetectedSdk(sdkHome));
    }
    return result;
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

  public List<Sdk> getAllPythonSdks() {
    return getAllPythonSdks(null);
  }
}
