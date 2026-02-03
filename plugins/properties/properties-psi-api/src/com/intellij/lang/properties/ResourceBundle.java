// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ResourceBundle {
  public static final DataKey<ResourceBundle[]> ARRAY_DATA_KEY = DataKey.create("resource.bundle.array");

  public abstract @NotNull List<PropertiesFile> getPropertiesFiles();

  public abstract @NotNull PropertiesFile getDefaultPropertiesFile();

  public abstract @NotNull String getBaseName();

  /**
   * @return null if properties files are not lying in the same directory
   */
  public abstract @Nullable VirtualFile getBaseDirectory();

  public @NotNull Project getProject() {
    return getDefaultPropertiesFile().getProject();
  }

  public abstract boolean isValid();
}
