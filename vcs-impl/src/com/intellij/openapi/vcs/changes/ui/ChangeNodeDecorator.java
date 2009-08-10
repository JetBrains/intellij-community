package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.ui.SimpleColoredComponent;

public interface ChangeNodeDecorator {
  void decorate(final Change change, final SimpleColoredComponent component);
}
