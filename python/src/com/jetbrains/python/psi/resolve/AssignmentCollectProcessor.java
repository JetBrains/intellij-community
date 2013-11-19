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
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.impl.PyQualifiedNameFactory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AssignmentCollectProcessor implements PsiScopeProcessor {
  /**
   * Collects all assignments in context above given element, if they match given naming pattern.
   * Used to track creation of attributes by assignment (e.g in constructor).
   */
  private final QualifiedName myQualifier;
  private final List<PyExpression> myResult;
  private final Set<String> mySeenNames;

  /**
   * Creates an instance to collect assignments of attributes to the object identified by 'qualifier'.
   * E.g. if qualifier = {"foo", "bar"} then assignments like "foo.bar.baz = ..." will be considered.
   *
   * @param qualifier qualifying names, outermost first; must not be empty.
   */
  public AssignmentCollectProcessor(@NotNull QualifiedName qualifier) {
    assert qualifier.getComponentCount() > 0;
    myQualifier = qualifier;
    myResult = new ArrayList<PyExpression>();
    mySeenNames = new HashSet<String>();
  }

  public boolean execute(@NotNull final PsiElement element, final ResolveState state) {
    if (element instanceof PyAssignmentStatement) {
      final PyAssignmentStatement assignment = (PyAssignmentStatement)element;
      for (PyExpression ex : assignment.getTargets()) {
        if (ex instanceof PyTargetExpression) {
          final PyTargetExpression target = (PyTargetExpression)ex;
          List<PyExpression> qualsExpr = PyResolveUtil.unwindQualifiers(target);
          QualifiedName qualifiedName = PyQualifiedNameFactory.fromReferenceChain(qualsExpr);
          if (qualifiedName != null) {
            if (qualifiedName.getComponentCount() == myQualifier.getComponentCount() + 1 && qualifiedName.matchesPrefix(myQualifier)) {
              // a new attribute follows last qualifier; collect it.
              PyExpression last_elt = qualsExpr.get(qualsExpr.size() - 1); // last item is the outermost, new, attribute.
              String last_elt_name = last_elt.getName();
              if (!mySeenNames.contains(last_elt_name)) { // no dupes, only remember the latest
                myResult.add(last_elt);
                mySeenNames.add(last_elt_name);
              }
            }
          }
        }

      }
    }
    return true; // nothing interesting found, continue
  }

  /**
   * @return a collection of expressions (parts of assignment expressions) where new attributes were defined. E.g. for "a.b.c = 1",
   *         the expression for 'c' is in the result.
   */
  @NotNull
  public Collection<PyExpression> getResult() {
    return myResult;
  }

  public <T> T getHint(@NotNull final Key<T> hintKey) {
    return null;
  }

  public void handleEvent(final Event event, final Object associated) {
    // empty
  }

}
