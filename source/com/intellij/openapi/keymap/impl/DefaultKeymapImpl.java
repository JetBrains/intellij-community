package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.KeymapManager;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 21, 2003
 * Time: 9:00:35 PM
 * To change this template use Options | File Templates.
 */
class DefaultKeymapImpl extends KeymapImpl {
  @NonNls
  private static final String DEFAULT = "Default";

  public boolean canModify() {
    return false;
  }

  public String getPresentableName() {
    String name = getName();
    return KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name) ? DEFAULT : name;
  }
}
