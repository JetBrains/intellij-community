package com.jetbrains.python.configuration;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;

import javax.swing.*;

/**
 * @author yole
*/
public class PythonSdkCellRenderer extends ColoredListCellRenderer {
  private String myNullText = "";

  public PythonSdkCellRenderer() {
  }

  public PythonSdkCellRenderer(final String nullText) {
    myNullText = nullText;
  }

  protected void customizeCellRenderer(final JList list,
                                       final Object value,
                                       final int index,
                                       final boolean selected,
                                       final boolean hasFocus) {
    Sdk sdk = (Sdk) value;
    if (sdk != null) {
      append(sdk.getName() + " (" + FileUtil.toSystemDependentName(sdk.getHomePath()) + ")", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else {
      append(myNullText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
