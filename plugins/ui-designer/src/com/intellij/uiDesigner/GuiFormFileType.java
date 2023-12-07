// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GuiFormFileType implements /*UIBased*/FileType {

  public static final GuiFormFileType INSTANCE = new GuiFormFileType();

  public static final @NonNls String DEFAULT_EXTENSION = "form";
  public static final @NonNls String DOT_DEFAULT_EXTENSION = "." + DEFAULT_EXTENSION;

  private GuiFormFileType() {
  }

  @Override
  public @NotNull String getName() {
    return "GUI_DESIGNER_FORM";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeBundle.message("filetype.gui.designer.form.description");
  }

  @Override
  public @Nls
  @NotNull String getDisplayName() {
    return IdeBundle.message("filetype.gui.designer.form.display.name");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return PlatformIcons.UI_FORM_ICON;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, final byte @NotNull [] content) {
    return CharsetToolkit.UTF8;
  }
}
