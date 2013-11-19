/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PSI element that (re)defines names in following namespace, e.g. as assignment statement does.
 *
 * NOTE: When declaring additional elements as name definers, please also adjust the token set via
 * {@link com.jetbrains.python.PythonDialectsTokenSetContributor#getNameDefinerTokens()}.
 *
 * @author dcheryasov
 */
public interface NameDefiner extends PsiElement {
  /**
   * @return an iterator that iterates over defined names, in order of definition.
   * Complex targets count, too: "(y, x[1]) = (1, 2)" return both "y" and "x[1]".
   */
  @NotNull
  Iterable<PyElement> iterateNames();

  /**
   * @param name an unqualified name.
   * @return an element which is defined under that name in this instance, or null. 
   */
  @Nullable
  PsiElement getElementNamed(String name);

  /**
   * @return true if names found inside its children cannot be resolved to names defined by this statement.
   * E.g. name <tt>a</tt> is defined in statement <tt>a = a + 1</tt> but the <tt>a</tt> on the right hand side
   * must not resolve to the <tt>a</tt> on the left hand side.
   */
  boolean mustResolveOutside();


  class IterHelper {  // TODO: rename sanely; move to a proper place.
    private IterHelper() {}
    @Nullable
    public static PyElement findName(Iterable<PyElement> it, String name) {
      PyElement ret = null;
      for (PyElement elt : it) {
        if (elt != null) {
          // qualified refs don't match by last name, and we're not checking FQNs here
          if (elt instanceof PyQualifiedExpression && ((PyQualifiedExpression)elt).getQualifier() != null) continue;
          if (name.equals(elt.getName())) { // plain name matches
            ret = elt;
            break;
          }
        }
      }
      return ret;
    }
  }

}
