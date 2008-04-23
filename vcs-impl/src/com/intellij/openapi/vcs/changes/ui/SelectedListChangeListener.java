package com.intellij.openapi.vcs.changes.ui;

import java.util.EventListener;

public interface SelectedListChangeListener extends EventListener {
  void selectedListChanged();
}
