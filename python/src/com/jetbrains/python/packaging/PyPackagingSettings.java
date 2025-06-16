// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
@ApiStatus.Internal
@State(name = "PyPackaging")
public class PyPackagingSettings implements PersistentStateComponent<PyPackagingSettings>, Disposable {

  public volatile boolean earlyReleasesAsUpgrades = false;

  public static PyPackagingSettings getInstance(@NotNull Project project) {
    return project.getService(PyPackagingSettings.class);
  }

  @Override
  public @Nullable PyPackagingSettings getState() {
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
  public @Nullable String selectLatestVersion(@NotNull List<String> versions) {
    if (!earlyReleasesAsUpgrades) {
      return StreamEx
        .of(versions)
        .findFirst(
          version -> {
            final PyPackageVersion normalized = PyPackageVersionNormalizer.normalize(version);

            return normalized == null || normalized.getPre() == null && normalized.getDev() == null;
          }
        )
        .orElse(null);
    }

    return ContainerUtil.getFirstItem(versions);
  }

  @Override
  public void dispose() {
  }
}
