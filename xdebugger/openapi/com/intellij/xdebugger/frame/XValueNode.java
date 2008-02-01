package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public interface XValueNode {

  void setPresentation(@NotNull String name, @Nullable Icon icon, @Nullable String type, @NotNull String value, boolean hasChildren);
  
}
