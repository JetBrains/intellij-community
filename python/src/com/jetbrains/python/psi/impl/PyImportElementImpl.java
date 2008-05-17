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

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.06.2005
 * Time: 22:22:35
 * To change this template use File | Settings | File Templates.
 */
public class PyImportElementImpl extends PyElementImpl implements PyImportElement {
  public PyImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Nullable
  public PyReferenceExpression getImportReference() {
    final ASTNode importRefNode = getNode().findChildByType(PyElementTypes.REFERENCE_EXPRESSION);
    if (importRefNode == null) return null;
    return (PyReferenceExpression)importRefNode.getPsi();
  }

  public PyTargetExpression getAsName() {
    final ASTNode asNameNode = getNode().findChildByType(PyElementTypes.TARGET_EXPRESSION);
    if (asNameNode == null) return null;
    return (PyTargetExpression)asNameNode.getPsi();
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor, @NotNull final ResolveState state, final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    // import is per-file
    if (place.getContainingFile() != getContainingFile()) {
      return true;
    }
    final PyReferenceExpression importRef = getImportReference();
    if (importRef != null) {
      final PsiElement element = importRef.resolve();
      if (element != null) {
        if (processor instanceof PyScopeProcessor) {
          PyTargetExpression asName = getAsName();
          if (asName != null) {
            return ((PyScopeProcessor) processor).execute(asName, asName.getText()); // execute 'element' to go directly to imported source
          }
          // maybe the incoming name is qualified
          PyReferenceExpression place_ref = PsiTreeUtil.getChildOfType(place, PyReferenceExpression.class);
          if (place_ref != null) {
            // unfold all qualifiers of import and reference and compare them
            List<PyReferenceExpression> place_path = unwindRefPath(place_ref);
            List<PyReferenceExpression> ref_path = unwindRefPath(importRef);
            if (matchPaths(place_path, ref_path)) {
              assert ref_path != null; // extraneous, but makes npe inspection happy
              for (PyReferenceExpression rex: ref_path) { // the thing the processor is looking for must be somewhere here
                final PsiElement elt = rex.resolve();
                if (!processor.execute(elt, state)) return false;
              }
              return true; // none matched
            }
          }
        }
        return processor.execute(element, state);
      }
    }
    return true;
  }

  // NOTE: to be moved to more general scope
  /**
   * Tries to match two [qualified] reference expression paths; target must be a 'sublist' of source to match.
   * E.g., 'a.b.c.d' and 'a.b.c' would match, while 'a.b.c' and 'a.b.c.d' would not. Eqaully, 'a.b.c' and 'a.b.d' would not match.
   * If either source or target is null, false is returned.
   * @see #unwindRefPath(PyReferenceExpression).
   * @param source_path expression path to match (the longer list of qualifiers).
   * @param target_path expression path to match against (hopeful sublist of qualifiers of source).
   * @return true if source matches target.
   */
  public static boolean matchPaths(List<PyReferenceExpression> source_path, List<PyReferenceExpression> target_path) {
    // turn qualifiers into lists
    if ((source_path == null) || (target_path == null)) return false;
    // compare until target is exhausted
    Iterator<PyReferenceExpression> source_iter = source_path.iterator();
    for (final PyReferenceExpression target_elt : target_path) {
      if (source_iter.hasNext()) {
        PyReferenceExpression source_elt = source_iter.next();
        if (!target_elt.getText().equals(source_elt.getText())) return false;
      }
      else return false; // source exhausted before target
    }
    return true;
  }

  /**
   * Unwinds a [multi-level] qualified expression into a path, as seen in source text, i.e. outermost qualifier first.
   * If any qualifier happens to be not a referencce expression, or expr is null, null is returned.
   * @param expr an experssion to unwind.
   * @return path as a list of ref expressions, or null.
   */
  @Nullable
  protected static List<PyReferenceExpression> unwindRefPath(final PyReferenceExpression expr) {
    final List<PyReferenceExpression> path = new LinkedList<PyReferenceExpression>();
    PyExpression maybe_step;
    PyReferenceExpression step = expr;
    try {
      while (step != null) {
        path.add(0, step);
        maybe_step = step.getQualifier();
        step = (PyReferenceExpression)maybe_step;
      }
    }
    catch (ClassCastException e) {
      return null;
    }
    return path;
  }
}
