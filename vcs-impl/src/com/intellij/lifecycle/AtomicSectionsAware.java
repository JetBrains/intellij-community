package com.intellij.lifecycle;

public interface AtomicSectionsAware {
  void enter();
  void exit();
  boolean shouldExitAsap();
}
