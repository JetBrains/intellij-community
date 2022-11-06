package com.jetbrains.performancePlugin.profilers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public interface SnapshotOpener {
  ExtensionPointName<SnapshotOpener> EP_NAME = new ExtensionPointName<>("com.jetbrains.performancePlugin.snapshotOpener");

  @Nullable
  static SnapshotOpener findSnapshotOpener(@NotNull File snapshot) {
    return EP_NAME.getExtensionList().stream().filter(p -> p.canOpen(snapshot)).findAny().orElse(null);
  }

  boolean canOpen(@NotNull File snapshot);

  void open(@NotNull File snapshot, @NotNull Project project);

}
