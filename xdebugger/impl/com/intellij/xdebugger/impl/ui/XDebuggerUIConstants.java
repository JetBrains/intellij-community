package com.intellij.xdebugger.impl.ui;

import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XDebuggerUIConstants {
  public static final String COLLECTING_DATA_MESSAGE = XDebuggerBundle.message("xdebugger.building.tree.node.message");
  public static final Icon ERROR_MESSAGE_ICON = IconLoader.getIcon("/debugger/db_error.png");
  public static final SimpleTextAttributes COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.lightGray);
  public static final SimpleTextAttributes CHAGED_VALUE_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.blue);
  public static final SimpleTextAttributes EXCEPTION_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.red);

  private XDebuggerUIConstants() {
  }
}
