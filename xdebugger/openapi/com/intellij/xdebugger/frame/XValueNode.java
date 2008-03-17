package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public interface XValueNode extends Obsolescent {

  void setPresentation(@NonNls @NotNull String name, @Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String value, boolean hasChildren);
  
}
