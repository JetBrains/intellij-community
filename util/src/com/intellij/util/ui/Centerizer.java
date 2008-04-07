package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class Centerizer extends JPanel {

  public Centerizer(@NotNull JComponent comp) {
    setOpaque(false);
    setBorder(null);

    add(comp);
  }

  @Nullable
  private Component getComponent() {
    if (getComponentCount() != 1) return null;
    return getComponent(0);
  }

  public void doLayout() {
    final Component c = getComponent();
    if (c == null) return;

    final Dimension compSize = c.getPreferredSize();

    final Dimension size = getSize();

    final Pair<Integer, Integer> x = getFit(compSize.width, size.width);
    final Pair<Integer, Integer> y = getFit(compSize.height, size.height);

    c.setBounds(x.first.intValue(), y.first.intValue(), x.second.intValue(), y.second.intValue());
  }

  private static Pair<Integer, Integer> getFit(int compSize, int containerSize) {
    if (compSize >= containerSize) {
      return new Pair<Integer, Integer>(0, compSize);
    } else {
      final int position = containerSize / 2 - compSize / 2;
      return new Pair<Integer, Integer>(position, compSize);
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  public Dimension getPreferredSize() {
    return getComponent() != null ? getComponent().getPreferredSize() : super.getPreferredSize();
  }

  @SuppressWarnings({"ConstantConditions"})
  public Dimension getMinimumSize() {
    return getComponent() != null ? getComponent().getMinimumSize() : super.getPreferredSize();
  }

  @SuppressWarnings({"ConstantConditions"})
  public Dimension getMaximimSize() {
    return getComponent() != null ? getComponent().getMaximumSize() : super.getPreferredSize();
  }
}
