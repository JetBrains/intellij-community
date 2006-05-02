package com.intellij.uiDesigner.make;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.uiDesigner.actions.PreviewFormAction;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class CopyResourcesUtil {
  private CopyResourcesUtil() {
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static File copyClass(final String targetPath, @NonNls final String className, final boolean deleteOnExit) throws IOException{
    final File targetDir = new File(targetPath).getAbsoluteFile();
    final File file = new File(targetDir, className + ".class");
    FileUtil.createParentDirs(file);
    if (deleteOnExit) {
      for (File f = file; f != null && !f.equals(targetDir); f = f.getParentFile()) {
        f.deleteOnExit();
      }
    }
    final String resourceName = "/" + className + ".class";
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

    @NonNls File jgoodiesFormsFile = new File(PathManager.getLibPath(), "jgoodies-forms.jar");
    @NonNls File jgoodiesFormsTarget = new File(targetDir, "jgoodies-forms.jar");
    FileUtil.copy(jgoodiesFormsFile, jgoodiesFormsTarget);
    jgoodiesFormsTarget.deleteOnExit();
  }
}
