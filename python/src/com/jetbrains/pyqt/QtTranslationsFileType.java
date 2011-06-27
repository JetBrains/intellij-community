package com.jetbrains.pyqt;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.IconLoader;

/**
 * @author yole
 */
public class QtTranslationsFileType extends QtFileType implements FileType {
  public static QtTranslationsFileType INSTANCE = new QtTranslationsFileType();

  protected QtTranslationsFileType() {
    super("Qt translations file", "Qt Linguist translations file", "ts", IconLoader.getIcon("/com/jetbrains/pyqt/tsFile.png"));
  }

  protected String getToolName() {
    return "linguist";
  }

  @Override
  public boolean useNativeIcon() {
    return false;
  }
}
