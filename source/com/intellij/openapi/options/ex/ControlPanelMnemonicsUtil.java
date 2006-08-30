/**
 * @author cdr
 */
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;

import java.awt.event.KeyEvent;

public class ControlPanelMnemonicsUtil {
  private ControlPanelMnemonicsUtil() {
  }

  public static Configurable getConfigurableFromMnemonic(KeyEvent e, ConfigurableGroup[] groups) {
    if (e.getModifiers() != 0) return null;
    ConfigurableGroup group = getGroupFromKeycode(e.getKeyCode(), groups);
    if (group == null) return null;

    int idx = getIndexFromKeycode(e.getKeyCode(), group == groups[0]);
    if (idx < 0 || idx >= group.getConfigurables().length) return null;

    return group.getConfigurables()[idx];
  }

  public static int getIndexFromKeycode(int keyCode, boolean isNumeric) {
    if (isNumeric) {
      if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9) return keyCode - KeyEvent.VK_1;
      if (keyCode >= KeyEvent.VK_NUMPAD1 && keyCode <= KeyEvent.VK_NUMPAD9) return keyCode - KeyEvent.VK_NUMPAD1;
      if (keyCode == KeyEvent.VK_NUMPAD0 || keyCode == KeyEvent.VK_0) return 9;
    }
    else {
      if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) return keyCode - KeyEvent.VK_A;
    }
    return -1;
  }

  public static ConfigurableGroup getGroupFromKeycode(int keyCode, ConfigurableGroup[] groups) {
    if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9 ||
        keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9) {
      return groups[0];
    }

    if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z && groups.length > 1) return groups[1];

    return null;
  }
}