/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  public String getName() {
    return "IPNB";
  }

  @NotNull
  public String getDescription() {
    return "Jupyter Notebook";
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

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
