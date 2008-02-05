package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author nik
 */
public interface XValueNode {

  void setPresentation(@NonNls @NotNull String name, @Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String value, boolean hasChildren);
  
}
