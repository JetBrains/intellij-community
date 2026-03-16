// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory;
import com.jetbrains.python.run.TargetConfigurationWithLocalFsAccessExKt;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote;

/**
 * Manages the SDK model shared between PythonSdkConfigurable and PyActiveSdkConfigurable.
 */
@Service(Service.Level.PROJECT)
public final class PyConfigurableInterpreterList {
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
  @ApiStatus.Internal
  public @NotNull List<Sdk> getAllPythonSdks(@Nullable Module module) {
    var targetModuleSitsOn = (module != null)
                             ? PythonInterpreterTargetEnvironmentFactory.Companion.getTargetModuleResidesOn(module)
                             : null;

    List<Sdk> result = new ArrayList<>();
    for (Sdk sdk : getModel().getSdks()) {
      if (!PythonSdkUtil.isPythonSdk(sdk)) continue;
      if (targetModuleSitsOn != null) {
        var sdkConfig = PySdkExtKt.getTargetEnvConfiguration(sdk);
        if (!TargetConfigurationWithLocalFsAccessExKt.codeCouldProbablyBeRunWithConfig(targetModuleSitsOn, sdkConfig)) {
          continue;
        }
      }
      result.add(sdk);
    }
    result.sort(new PyInterpreterComparator());
    return result;
  }

  /**
   * Returns all Python SDKs visible across the IDE.
   *
   * @deprecated The global SDK table is being replaced with per-project SDK visibility.
   */
  @Deprecated
  public @NotNull List<Sdk> getAllPythonSdks() {
    return getAllPythonSdks(null);
  }

  private static class PyInterpreterComparator implements Comparator<Sdk> {
    @Override
    public int compare(@NotNull Sdk o1, Sdk o2) {
      // Remote SDKs last
      final boolean isRemote1 = isRemote(o1);
      final boolean isRemote2 = isRemote(o2);
      if (isRemote1 != isRemote2) return isRemote1 ? 1 : -1;

      return Comparing.compare(o1.getName(), o2.getName());
    }
  }
}
