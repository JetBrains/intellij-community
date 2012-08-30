package com.jetbrains.pyqt;

import com.intellij.openapi.fileTypes.FileType;
import icons.PythonIcons;

/**
 * @author yole
 */
public class QtTranslationsFileType extends QtFileType implements FileType {
  public static QtTranslationsFileType INSTANCE = new QtTranslationsFileType();

  protected QtTranslationsFileType() {
    super("Qt translations file", "Qt Linguist translations files", "ts", PythonIcons.Pyqt.TsFile);
  }

  protected String getToolName() {
    return "linguist";
  }

  @Override
  public boolean useNativeIcon() {
    return false;
  }
}
