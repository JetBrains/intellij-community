// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.ipnb;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class IpnbFileType implements FileType {

  public static final IpnbFileType INSTANCE = new IpnbFileType();

  @NonNls
  public static final String DEFAULT_EXTENSION = "ipynb";

  @Override
  @NotNull
  public String getName() {
    return "IPNB";
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Jupyter Notebook";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.IpythonNotebook;
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull final byte[] content) {
    return CharsetToolkit.UTF8;
  }
}
