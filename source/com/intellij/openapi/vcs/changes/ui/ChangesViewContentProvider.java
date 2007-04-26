package com.intellij.openapi.vcs.changes.ui;

import javax.swing.*;

/**
 * @author yole
 */
public interface ChangesViewContentProvider {
  JComponent initContent();
  void disposeContent();
}
