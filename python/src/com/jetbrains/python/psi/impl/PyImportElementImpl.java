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
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyAsScopeProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * The "import foo" or "import foo as bar" parts.
 * User: yole
 * Date: 02.06.2005
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

  @Nullable
  public String getVisibleName() {
    PyTargetExpression asname = getAsName();
    if (asname != null) return asname.getName();
    for (PyElement name_elt : iterateNames()) {
      return name_elt.getName(); // first to come must be right
    }
    return null; // we might have not found any names
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
        if (processor instanceof PyAsScopeProcessor) {
          PyTargetExpression asName = getAsName();
          if (asName != null) {
            return ((PyAsScopeProcessor) processor).execute(element, asName.getText()); // might resolve to asName to show the source of name
          }
          // maybe the incoming name is qualified
          PyReferenceExpression place_ref = PsiTreeUtil.getChildOfType(place, PyReferenceExpression.class);
          if (place_ref != null) {
            // unfold all qualifiers of import and reference and compare them
            List<PyReferenceExpression> place_path = PyResolveUtil.unwindQualifiers(place_ref);
            List<PyReferenceExpression> ref_path = PyResolveUtil.unwindQualifiers(importRef);
            if (PyResolveUtil.pathsMatch(place_path, ref_path)) {
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

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      @NotNull
      private String getRefName(String default_name) {
        PyReferenceExpression ref = getImportReference();
        if (ref != null) {
          String refname = ref.getName();
          if (refname != null) return refname;
        }
        return default_name;
      }

      public String getPresentableText() {
        return getRefName("<none>");
      }

      public String getLocationString() {
        PyElement elt = PsiTreeUtil.getParentOfType(PyImportElementImpl.this, PyImportStatement.class, PyFromImportStatement.class);
        StringBuffer buf = new StringBuffer("| ");
        if (elt != null) { // always? who knows :)
          if (elt instanceof PyFromImportStatement) { // from ... import ...
            buf.append("from ");
            PyReferenceExpression imp_src = ((PyFromImportStatement)elt).getImportSource();
            if (imp_src != null) {
              buf.append(PyResolveUtil.toPath(imp_src, "."));
            }
            else buf.append("<?>");
            buf.append(" import ");
          }
          else { // "import ... "
            buf.append("import ");
          }
          buf.append(getRefName("<?>"));
        }
        else {
          buf.append("import?.. ");
        }
        // are we the name or the 'as'?
        PyTargetExpression as_part = getAsName();
        if (as_part != null) {
          buf.append(" as ").append(as_part.getName());
        }
        return buf.toString();
      }

      public Icon getIcon(final boolean open) {
        return null;
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyElement ret = getAsName();
    if (ret == null) {
      List<PyReferenceExpression> unwound_path = PyResolveUtil.unwindQualifiers(getImportReference());
      if ((unwound_path != null) && (unwound_path.size() > 0)) ret = unwound_path.get(0); 
    }
    return new SingleIterable<PyElement>(ret);
  }

  public PsiElement getElementNamed(final String the_name) {
    PyElement named_elt = IterHelper.findName(iterateNames(), the_name);
    if (named_elt != null) {
      PyReferenceExpression import_ref = getImportReference(); // = most qualified import name: "z" for "import x.y.z"
      if (getAsName() == null) { // the match was not by target expr of "import ... as foo"
        if (named_elt instanceof PyReferenceExpression) import_ref  = (PyReferenceExpression)named_elt; // [part of] import ref matched
        else return null; // I wonder what could have matched there?
      }
      return ResolveImportUtil.resolveImportReference(import_ref);
    }
    // no element of this name
    return null;
  }

  public boolean mustResolveOutside() {
    return true; // formally
  }
}
