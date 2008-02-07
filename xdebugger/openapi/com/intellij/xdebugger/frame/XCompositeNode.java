package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface XCompositeNode {

  void setChildren(List<XValue> children);

  void setErrorMessage(@NotNull String errorMessage);
}
