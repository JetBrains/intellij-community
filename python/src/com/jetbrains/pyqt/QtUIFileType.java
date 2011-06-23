package com.jetbrains.pyqt;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.IconLoader;

/**
 * @author yole
 */
public class QtUIFileType extends QtFileType implements FileType {
  public static QtUIFileType INSTANCE = new QtUIFileType();

  protected QtUIFileType() {
    super("Qt UI file", "Qt UI Designer form file", "ui", IconLoader.getIcon("/com/jetbrains/pyqt/uiForm.png"));
  }

  protected String getToolName() {
    return "designer";
  }

  @Override
  public boolean useNativeIcon() {
    return false;
  }
}
