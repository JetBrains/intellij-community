package com.intellij.openapi.ui;

import com.intellij.util.ObjectUtils;

import java.awt.*;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface Painter {

  boolean needsRepaint();
  void paint(Component component, final Graphics2D g);

  void addListener(Listener listener);
  void removeListener(Listener listener);

  interface Listener {
    void onNeedsRepaint(Painter painter, @Nullable JComponent dirtyComponent);
  }

}
