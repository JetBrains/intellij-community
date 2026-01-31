// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.remote.vfs;

import com.intellij.openapi.fileTypes.FileType;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public class PyRemoteDebugFileType implements FileType {
  public static final PyRemoteDebugFileType INSTANCE = new PyRemoteDebugFileType();

  private PyRemoteDebugFileType() {
  }

  @Override
  public @NotNull String getName() {
    return PyBundle.message("python.debug.remote.name");
  }

  @Override
  public @NotNull String getDescription() {
    return PyBundle.message("filetype.python.debug.remote.description");
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return "py";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }
}
