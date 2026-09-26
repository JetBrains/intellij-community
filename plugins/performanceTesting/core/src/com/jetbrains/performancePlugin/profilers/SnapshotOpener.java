// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.profilers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public interface SnapshotOpener {
  ExtensionPointName<SnapshotOpener> EP_NAME = new ExtensionPointName<>("com.jetbrains.performancePlugin.snapshotOpener");

  static @Nullable SnapshotOpener findSnapshotOpener(@NotNull File snapshot, @Nullable Project project) {
    return ContainerUtil.find(EP_NAME.getExtensionList(), p -> p.canOpen(snapshot, project));
  }

  boolean canOpen(@NotNull File snapshot, @Nullable Project project);

  @Nls
  String getPresentableName();

  void open(@NotNull File snapshot, @Nullable Project project);
}
