package com.intellij.openapi.roots.impl.libraries;

import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;

/**
 *  @author dsl
 */
public interface LibraryEx extends Library {
  Library cloneLibrary();
  void setRootModel(ModifiableRootModel rootModel);
  boolean allPathsValid(OrderRootType type);
}
