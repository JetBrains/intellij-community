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
package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

/**
 * @author traff
 */
public class MayaSdkFlavor extends PythonSdkFlavor {
  private MayaSdkFlavor() {
  }

  public static MayaSdkFlavor INSTANCE = new MayaSdkFlavor();

  public boolean isValidSdkHome(String path) {
    File file = new File(path);
    return (file.isFile() && isValidSdkPath(file)) || isMayaFolder(file);
  }

  private static boolean isMayaFolder(File file) {
    return file.isDirectory() && file.getName().equals("Maya.app");
  }

  public boolean isValidSdkPath(@NotNull File file) {
    String name = FileUtil.getNameWithoutExtension(file).toLowerCase();
    return name.startsWith("mayapy");
  }

  public String getVersionOption() {
    return "--version";
  }

  @NotNull
  @Override
  public String getName() {
    return "MayaPy";
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Python; //TODO: maya icon
  }

  @Override
  public VirtualFile getSdkPath(VirtualFile path) {
    if (isMayaFolder(new File(path.getPath()))) {
      return path.findFileByRelativePath("Contents/bin/mayapy");
    }
    return path;
  }
}
