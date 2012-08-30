package com.jetbrains.pyqt;

import com.intellij.openapi.fileTypes.FileType;
import icons.PythonIcons;

/**
 * @author yole
 */
public class QtUIFileType extends QtFileType implements FileType {
  public static QtUIFileType INSTANCE = new QtUIFileType();

  protected QtUIFileType() {
    super("Qt UI file", "Qt UI Designer form files", "ui", PythonIcons.Pyqt.UiForm);
  }

  protected String getToolName() {
    return "designer";
  }

  @Override
  public boolean useNativeIcon() {
    return false;
  }
}
