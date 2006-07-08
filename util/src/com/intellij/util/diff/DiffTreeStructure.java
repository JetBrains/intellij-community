package com.intellij.util.diff;

import java.util.List;

/**
 * @author max
 */
public interface DiffTreeStructure<T> {
  T prepareForGetChildren(T node);
  T getRoot();
  List<T> getChildren(T parent);
}
