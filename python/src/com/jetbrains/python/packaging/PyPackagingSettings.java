// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.python.packaging.requirement.PyRequirementVersion;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(name = "PyPackaging")
public class PyPackagingSettings implements PersistentStateComponent<PyPackagingSettings> {

  public volatile boolean earlyReleasesAsUpgrades = false;

  public static PyPackagingSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PyPackagingSettings.class);
  }

  @Nullable
  @Override
  public PyPackagingSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyPackagingSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  /**
   * @param versions package versions sorted from the freshest to the oldest one
   * @return first item in {@code versions} if {@link PyPackagingSettings#earlyReleasesAsUpgrades} is true,
   * first non-pre- and non-developmental release otherwise.
   */
  @Nullable
  public String selectLatestVersion(@NotNull List<String> versions) {
    if (!earlyReleasesAsUpgrades) {
      return StreamEx
        .of(versions)
        .findFirst(
          version -> {
            final PyRequirementVersion normalized = PyRequirementVersionNormalizer.normalize(version);

            return normalized == null || normalized.getPre() == null && normalized.getDev() == null;
          }
        )
        .orElse(null);
    }

    return ContainerUtil.getFirstItem(versions);
  }
}
