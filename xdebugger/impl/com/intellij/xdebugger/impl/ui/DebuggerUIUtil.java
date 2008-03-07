package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 11:26:44 PM
 */
public class DebuggerUIUtil {
  private DebuggerUIUtil() {
  }

  public static void enableEditorOnCheck(final JCheckBox checkbox, final JComponent textfield) {
    checkbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        boolean selected = checkbox.isSelected();
        textfield.setEnabled(selected);
      }
    });
    textfield.setEnabled(checkbox.isSelected());
  }

  public static void focusEditorOnCheck(final JCheckBox checkbox, final JComponent component) {
    final Runnable runnable = new Runnable() {
      public void run() {
        component.requestFocus();
      }
    };
    checkbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (checkbox.isSelected()) {
          SwingUtilities.invokeLater(runnable);
        }
      }
    });
  }

  public static void invokeLater(final Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  public static void invokeOnEventDispatch(final Runnable runnable) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }
}
