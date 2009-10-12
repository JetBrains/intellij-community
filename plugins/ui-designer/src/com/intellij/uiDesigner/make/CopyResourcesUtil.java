/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.make;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.uiDesigner.actions.PreviewFormAction;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class CopyResourcesUtil {
  private CopyResourcesUtil() {
  }

  public static File copyClass(final String targetPath, @NonNls final String className, final boolean deleteOnExit) throws IOException{
    final File targetDir = new File(targetPath).getAbsoluteFile();
    final File file = new File(targetDir, className + ".class");
    FileUtil.createParentDirs(file);
    if (deleteOnExit) {
      for (File f = file; f != null && !f.equals(targetDir); f = f.getParentFile()) {
        f.deleteOnExit();
      }
    }
    @NonNls final String resourceName = "/" + className + ".class";
    final InputStream stream = PreviewFormAction.class.getResourceAsStream(resourceName);
    if (stream == null) {
      throw new IOException("cannot load " + resourceName);
    }
    return copyStreamToFile(stream, file);
  }

  private static File copyStreamToFile(final InputStream stream, final File file) throws IOException {
    try {
      final FileOutputStream outputStream = new FileOutputStream(file);
      try {
        FileUtil.copy(stream, outputStream);
      }
      finally {
        outputStream.close();
      }
    }
    finally {
      stream.close();
    }
    return file;
  }

  public static void copyProperties(final String targetPath, final String fileName) throws IOException {
    final File targetDir = new File(targetPath).getAbsoluteFile();
    final File file = new File(targetDir, fileName);
    FileUtil.createParentDirs(file);
    for (File f = file; f != null && !f.equals(targetDir); f = f.getParentFile()) {
      f.deleteOnExit();
    }
    final String resourceName = "/" + fileName;
    final InputStream stream = PreviewFormAction.class.getResourceAsStream(resourceName);
    if (stream == null) {
      return;
    }
    copyStreamToFile(stream, file);
  }

  public static void copyFormsRuntime(final String targetDir, final boolean deleteOnExit) throws IOException {
    copyClass(targetDir, "com/intellij/uiDesigner/core/AbstractLayout", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/DimensionInfo", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/GridConstraints", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/GridLayoutManager", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/HorizontalInfo", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/LayoutState", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/Spacer", deleteOnExit);
    //copyClass(targetDir, "com/intellij/uiDesigner/core/SupportCode$1", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/SupportCode$TextWithMnemonic", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/SupportCode", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/Util", deleteOnExit);
    copyClass(targetDir, "com/intellij/uiDesigner/core/VerticalInfo", deleteOnExit);
  }
}
