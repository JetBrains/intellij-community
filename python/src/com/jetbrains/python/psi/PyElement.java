/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
