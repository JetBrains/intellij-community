package com.jetbrains.python.psi;

import com.intellij.psi.NavigatablePsiElement;

public interface PyElement extends NavigatablePsiElement {

  /**
   * An empty array to return cheaply without allocating it anew.
   */
  PyElement[] EMPTY_ARRAY = new PyElement[0];

}
