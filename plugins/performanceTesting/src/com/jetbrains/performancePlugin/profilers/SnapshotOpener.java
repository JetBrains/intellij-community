package com.jetbrains.performancePlugin.profilers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public interface SnapshotOpener {
  ExtensionPointName<SnapshotOpener> EP_NAME = new ExtensionPointName<>("com.jetbrains.performancePlugin.snapshotOpener");

  @Nullable
  static SnapshotOpener findSnapshotOpener(@NotNull File snapshot) {
    return ContainerUtil.find(EP_NAME.getExtensionList(), p -> p.canOpen(snapshot));
  }

  boolean canOpen(@NotNull File snapshot);

  void open(@NotNull File snapshot, @NotNull Project project);

}
