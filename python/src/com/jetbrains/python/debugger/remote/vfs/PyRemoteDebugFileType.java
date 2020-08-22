// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.remote.vfs;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PyRemoteDebugFileType implements FileType {
  public static final PyRemoteDebugFileType INSTANCE = new PyRemoteDebugFileType();

  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("python.debug.remote.name");
  }

  @NotNull
  @Override
  public String getDescription() {
    return PyBundle.message("python.debug.remote.description");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
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

  @Override
  public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    return null;
  }
}
