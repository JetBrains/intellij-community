package com.intellij.xdebugger.stepping;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class XSmartStepIntoVariant {
  @Nullable
  public Icon getIcon() {
    return null;
  }

  public abstract String getText();

}
