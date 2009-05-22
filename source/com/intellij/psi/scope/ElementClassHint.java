package com.intellij.psi.scope;

import com.intellij.openapi.util.Key;

public interface ElementClassHint {
  Key<ElementClassHint> KEY = Key.create("ElementClassHint");
  boolean shouldProcess(Class elementClass);
}
