package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;

/**
 * @author max
 */
public interface DiffTreeStructure<T> {
  T prepareForGetChildren(T node);
  T getRoot();
  int getChildren(T parent, Ref<T[]> into);
  void disposeChildren(T[] nodes, int count);
}
