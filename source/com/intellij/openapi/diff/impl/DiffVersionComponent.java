package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposeable;

public interface DiffVersionComponent {
  void addDisposable(Disposeable disposeable);
  void removeContent();
}
