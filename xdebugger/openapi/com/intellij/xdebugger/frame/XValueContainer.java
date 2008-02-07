package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author nik
 */
public abstract class XValueContainer {
  /**
   * Start computing children of the value and call {@link com.intellij.xdebugger.frame.XCompositeNode#setChildren(java.util.List)} if
   * computation is finished successfully or call {@link com.intellij.xdebugger.frame.XCompositeNode#setErrorMessage(String)} if an error occurs
   * @param node node in the tree
   */
  public void computeChildren(@NotNull XCompositeNode node) {
    node.setChildren(Collections.<XValue>emptyList());
  }
}
