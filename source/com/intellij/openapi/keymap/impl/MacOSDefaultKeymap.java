package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.actionSystem.Shortcut;

import javax.swing.*;
import java.awt.event.InputEvent;

/**
 * @author max
 */
class MacOSDefaultKeymap extends DefaultKeymapImpl {
  protected String[] getParentActionIds(KeyStroke firstKeyStroke) {
    return super.getParentActionIds(convertKeyStroke(firstKeyStroke));
  }

  protected String[] getParentActionIds(MouseShortcut shortcut) {
    return super.getParentActionIds(convertMouseShortcut(shortcut));
  }

  protected Shortcut[] getParentShortcuts(String actionId) {
    Shortcut[] parentShortcuts = super.getParentShortcuts(actionId);
    Shortcut[] macShortcuts = new Shortcut[parentShortcuts.length];
    for (int i = 0; i < parentShortcuts.length; i++) {
      macShortcuts[i] = convertShortcutFromParent(parentShortcuts[i]);
    }
    return macShortcuts;
  }

  private Shortcut convertShortcutFromParent(Shortcut parentShortcut) {
    if (parentShortcut instanceof MouseShortcut) {
      return convertMouseShortcut((MouseShortcut)parentShortcut);
    }

    KeyboardShortcut key = (KeyboardShortcut)parentShortcut;
    return new KeyboardShortcut(convertKeyStroke(key.getFirstKeyStroke()),
                                convertKeyStroke(key.getSecondKeyStroke()));
  }

  private KeyStroke convertKeyStroke(KeyStroke parentKeyStroke) {
    if (parentKeyStroke == null) return null;
    return KeyStroke.getKeyStroke(parentKeyStroke.getKeyCode(),
                                  mapModifiers(parentKeyStroke.getModifiers()),
                                  parentKeyStroke.isOnKeyRelease());
  }

  private MouseShortcut convertMouseShortcut(MouseShortcut macShortcut) {
    return new MouseShortcut(macShortcut.getButton(),
                             mapModifiers(macShortcut.getModifiers()),
                             macShortcut.getClickCount());
  }

  private int mapModifiers(int modifiers) {
    boolean meta = false;
    boolean metaDown = false;
    boolean control = false;
    boolean controlDown = false;

    if ((modifiers & InputEvent.META_MASK) != 0) {
      modifiers &= ~InputEvent.META_MASK;
      meta = true;
    }

    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
      modifiers &= ~InputEvent.META_DOWN_MASK;
      metaDown = true;
    }

    if ((modifiers & InputEvent.CTRL_MASK) != 0) {
      modifiers &= ~InputEvent.CTRL_MASK;
      control = true;
    }

    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
      modifiers &= ~InputEvent.CTRL_DOWN_MASK;
      controlDown = true;
    }

    if (meta) {
      modifiers |= InputEvent.CTRL_MASK;
    }

    if (metaDown) {
      modifiers |= InputEvent.CTRL_DOWN_MASK;
    }

    if (control) {
      modifiers |= InputEvent.META_MASK;
    }

    if (controlDown) {
      modifiers |= InputEvent.META_DOWN_MASK;
    }

    return modifiers;
  }
}
