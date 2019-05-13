// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.pyqt;

import com.intellij.openapi.fileTypes.FileType;
import icons.PythonIcons;

/**
 * @author yole
 */
public class QtUIFileType extends QtFileType implements FileType {
  public static QtUIFileType INSTANCE = new QtUIFileType();

  protected QtUIFileType() {
    super("Qt UI file", "Qt UI Designer form", "ui", PythonIcons.Pyqt.UiForm);
  }

  @Override
  protected String getToolName() {
    return "designer";
  }

  @Override
  public boolean useNativeIcon() {
    return false;
  }
}
