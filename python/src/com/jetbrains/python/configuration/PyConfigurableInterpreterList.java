// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.run.TargetConfigurationWithLocalFsAccessExKt;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages the SDK model shared between PythonSdkConfigurable and PyActiveSdkConfigurable.
 */
public class PyConfigurableInterpreterList {
  private ProjectSdksModel myModel;

  public static PyConfigurableInterpreterList getInstance(@Nullable Project project) {
    final Project effectiveProject = project != null ? project : ProjectManager.getInstance().getDefaultProject();
    final PyConfigurableInterpreterList instance = effectiveProject.getService(PyConfigurableInterpreterList.class);
    if (effectiveProject != project) {
      instance.disposeModel();
    }
    return instance;
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

  /**
   * @param module if not null and module resides on certain target, returns only SDKs for this target
   */
  public List<Sdk> getAllPythonSdks(@Nullable final Project project, @Nullable Module module) {
    var targetModuleSitsOn = (module != null)
                             ? PythonInterpreterTargetEnvironmentFactory.Companion.getTargetModuleResidesOn(module)
                             : null;

    List<Sdk> result = new ArrayList<>();
    for (Sdk sdk : getModel().getSdks()) {
      if (!(sdk.getSdkType() instanceof PythonSdkType)) continue;
      if (targetModuleSitsOn != null) {
        var sdkConfig = PySdkExtKt.getTargetEnvConfiguration(sdk);
        if (!TargetConfigurationWithLocalFsAccessExKt.codeCouldProbablyBeRunWithConfig(targetModuleSitsOn, sdkConfig)) {
          continue;
        }
      }
      result.add(sdk);
    }
    result.sort(new PyInterpreterComparator(project));
    return result;
  }

  public List<Sdk> getAllPythonSdks() {
    return getAllPythonSdks(null, null);
  }

  private static class PyInterpreterComparator implements Comparator<Sdk> {
    @Nullable private final Project myProject;

    PyInterpreterComparator(@Nullable final Project project) {
      myProject = project;
    }

    @Override
    public int compare(Sdk o1, Sdk o2) {
      if (!(o1.getSdkType() instanceof PythonSdkType) ||
          !(o2.getSdkType() instanceof PythonSdkType)) {
        return -Comparing.compare(o1.getName(), o2.getName());
      }

      final boolean isVEnv1 = PythonSdkUtil.isVirtualEnv(o1) || PythonSdkUtil.isCondaVirtualEnv(o1);
      final boolean isVEnv2 = PythonSdkUtil.isVirtualEnv(o2) || PythonSdkUtil.isCondaVirtualEnv(o2);
      final boolean isRemote1 = PythonSdkUtil.isRemote(o1);
      final boolean isRemote2 = PythonSdkUtil.isRemote(o2);

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
        final String path = data.getAssociatedModulePath();
        final String projectBasePath = project.getBasePath();
        if (path != null && path.equals(projectBasePath)) {
          return true;
        }
      }
      return false;
    }
  }
}
