package com.intellij.openapi.keymap.ex;

import com.intellij.openapi.keymap.Keymap;

public interface KeymapManagerListener {
  void activeKeymapChanged(Keymap keymap);
}
