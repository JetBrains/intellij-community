package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author max
 */
public class UIUtil {
  public static boolean isReallyTypedEvent(KeyEvent e) {
    char c = e.getKeyChar();
    if (!(c >= 0x20 && c != 0x7F)) return false;

    int modifiers = e.getModifiers();
    if (SystemInfo.isMac) {
      return !e.isMetaDown() && !e.isControlDown();
    }

    return (modifiers & ActionEvent.ALT_MASK) == (modifiers & ActionEvent.CTRL_MASK);
  }
}
