// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.ui;

import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class PerspectiveFileEditorProvider extends WeighedFileEditorProvider {
  @Override
  public abstract @NotNull PerspectiveFileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file);

  @Override
  public final @NotNull @NonNls String getEditorTypeId() {
    return getComponentName();
  }

  @Override
  public final @NotNull FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }

  public final @NonNls String getComponentName() {
    return getClass().getName();
  }
}
