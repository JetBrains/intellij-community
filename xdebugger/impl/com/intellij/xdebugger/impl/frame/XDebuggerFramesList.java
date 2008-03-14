package com.intellij.xdebugger.impl.frame;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XStackFrame;

import javax.swing.*;

/**
 * @author nik
 */
public class XDebuggerFramesList extends DebuggerFramesList {
  private XStackFrame mySelectedFrame;

  protected ListCellRenderer createListRenderer() {
    return new XDebuggerFrameListRenderer();
  }

  protected void onFrameChanged(final Object selectedValue) {
    if (mySelectedFrame != selectedValue) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          repaint();
        }
      });
      mySelectedFrame = (XStackFrame)selectedValue;
    }
  }

  private static class XDebuggerFrameListRenderer extends ColoredListCellRenderer {
    protected void customizeCellRenderer(final JList list,
                                         final Object value,
                                         final int index,
                                         final boolean selected,
                                         final boolean hasFocus) {
      if (value == null) {
        append(XDebuggerBundle.message("stack.frame.loading.text"), SimpleTextAttributes.GRAY_ATTRIBUTES);
        return;
      }
      if (value instanceof String) {
        append((String)value, SimpleTextAttributes.ERROR_ATTRIBUTES);
        return;
      }

      XStackFrame stackFrame = (XStackFrame)value;
      stackFrame.customizePresentation(this);
    }
  }
}
