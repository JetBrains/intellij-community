package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author nik
 */
public abstract class XValueContainer {
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setChildren(Collections.<XValue>emptyList());
  }
}
