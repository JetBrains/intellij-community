package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.KeymapManager;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 21, 2003
 * Time: 9:00:35 PM
 * To change this template use Options | File Templates.
 */
class DefaultKeymapImpl extends KeymapImpl {
  public boolean canModify() {
    return false;
  }

  public String getPresentableName() {
    String name = getName();
    return KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name) ? "Default" : name;
  }
}
