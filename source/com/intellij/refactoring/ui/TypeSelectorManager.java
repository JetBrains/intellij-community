package com.intellij.refactoring.ui;

/**
 * @author dsl
 */
public interface TypeSelectorManager {
  TypeSelector getTypeSelector();

  void setAllOccurences(boolean allOccurences);
}
