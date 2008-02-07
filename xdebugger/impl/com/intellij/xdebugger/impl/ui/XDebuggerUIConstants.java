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
  public static final String EVALUATING_EXPRESSION_MESSAGE = XDebuggerBundle.message("xdebugger.evaluating.expression.node.message");
  public static final String MODIFYING_VALUE_MESSAGE = XDebuggerBundle.message("xdebugger.modifiyng.value.node.message");
  public static final Icon ERROR_MESSAGE_ICON = IconLoader.getIcon("/debugger/db_error.png");
  public static final SimpleTextAttributes COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.lightGray);
  public static final SimpleTextAttributes EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.lightGray);
  public static final SimpleTextAttributes MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.blue);
  public static final SimpleTextAttributes CHANGED_VALUE_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.blue);
  public static final SimpleTextAttributes EXCEPTION_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.red);
  public static final SimpleTextAttributes VALUE_NAME_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, new Color(128, 0, 0));
  public static final SimpleTextAttributes ERROR_MESSAGE_ATTRIBUTES = new SimpleTextAttributes(Font.PLAIN, Color.red);

  private XDebuggerUIConstants() {
  }
}
