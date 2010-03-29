package com.jetbrains.python.psi;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;

public interface PyElement extends NavigatablePsiElement {

  /**
   * An empty array to return cheaply without allocating it anew.
   */
  PyElement[] EMPTY_ARRAY = new PyElement[0];

  /**
   * Find a parent element of specified class.
   * @param aClass the class to look for.
   * @param &lt;T> the class to look for and to return. (Logically the same as aClass, but Java fails to express this concisely.)
   * @return A parent element whose class is <tt>T</tt>, if it exists, or null.
   */
  @Nullable <T extends PyElement> T getContainingElement(Class<T> aClass);

  /**
   * Find a parent whose element type is in the set.
   * @param tokenSet a set of element types
   * @return A parent element whose element type belongs to tokenSet, if it exists, or null.
   */
  @Nullable PyElement getContainingElement(TokenSet tokenSet);
}
