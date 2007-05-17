package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface IdePopup {

  @Nullable
  Component getComponent();
  boolean dispatch(AWTEvent event);

  void requestFocus();
}
