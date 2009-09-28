package org.jetbrains.idea.svn.config;

import javax.swing.*;

public interface ValidationListener {
  void onError(final String text, final JComponent component, final boolean forbidSave);
  void onSuccess();
}
