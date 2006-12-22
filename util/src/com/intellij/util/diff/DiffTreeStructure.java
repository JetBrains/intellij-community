package com.intellij.util.diff;

import java.util.List;

/**
 * @author max
 */
public interface DiffTreeStructure<T> {
  T prepareForGetChildren(T node);
  T getRoot();
  void getChildren(T parent, List<T> into);
}
