// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.fileTypes.ex.FakeFileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ResourceBundleFileType extends FakeFileType {
  public static final ResourceBundleFileType INSTANCE = new ResourceBundleFileType();

  private ResourceBundleFileType() {
  }

  @Override
  public @NotNull String getName() {
    return "ResourceBundle";
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return PropertiesFileType.DEFAULT_EXTENSION;
  }

  @Override
  public @NotNull String getDescription() {
    return PropertiesBundle.message("filetype.resourcebundle.description");
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return PropertiesBundle.message("filetype.resourcebundle.display.name");
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    return file instanceof ResourceBundleAsVirtualFile;
  }

}
