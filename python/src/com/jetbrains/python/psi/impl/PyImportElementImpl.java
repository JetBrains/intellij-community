/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.EmptyIterable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * The "import foo" or "import foo as bar" parts.
 *
 * @author yole
 */
public class PyImportElementImpl extends PyBaseElementImpl<PyImportElementStub> implements PyImportElement {
  public PyImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyImportElementImpl(PyImportElementStub stub) {
    this(stub, PyElementTypes.IMPORT_ELEMENT);
  }

  public PyImportElementImpl(PyImportElementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Nullable
  public PyReferenceExpression getImportReferenceExpression() {
    final ASTNode node = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getReferenceExpressionTokens());
    return node == null ? null : (PyReferenceExpression) node.getPsi();
  }

  public QualifiedName getImportedQName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      return stub.getImportedQName();
    }
    final PyReferenceExpression importReference = getImportReferenceExpression();
    return importReference != null ? importReference.asQualifiedName() : null;
  }

  public PyTargetExpression getAsNameElement() {
    return findChildByType(PyElementTypes.TARGET_EXPRESSION);
  }

  public String getAsName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      final String asName = stub.getAsName();
      return StringUtil.isEmpty(asName) ? null : asName;
    }
    final PyTargetExpression element = getAsNameElement();
    return element != null ? element.getName() : null;
  }

  @Override
  @Nullable
  public String getVisibleName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      final String asName = stub.getAsName();
      if (!StringUtil.isEmpty(asName)) {
        return asName;
      }
      final QualifiedName importedName = stub.getImportedQName();
      if (importedName != null && importedName.getComponentCount() > 0) {
        return importedName.getComponents().get(0);
      }
    }
    else {
      PyTargetExpression asNameElement = getAsNameElement();
      if (asNameElement != null) {
        return asNameElement.getName();
      }
      final QualifiedName importedName = getImportedQName();
      if (importedName != null && importedName.getComponentCount() > 0) {
        return importedName.getComponents().get(0);
      }
    }
    return null; // we might have not found any names
  }

  @Nullable
  public PyStatement getContainingImportStatement() {
    final PyImportElementStub stub = getStub();
    PsiElement parent;
    if (stub != null) {
      parent = stub.getParentStub().getPsi();
    }
    else {
      parent = getParent();
    }
    return parent instanceof PyStatement ? (PyStatement)parent : null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      @NotNull
      private String getRefName(String default_name) {
        PyReferenceExpression ref = getImportReferenceExpression();
        if (ref != null) {
          String refName = ref.getName();
          if (refName != null) return refName;
        }
        return default_name;
      }

      public String getPresentableText() {
        return getRefName("<none>");
      }

      public String getLocationString() {
        PyElement elt = PsiTreeUtil.getParentOfType(PyImportElementImpl.this, PyImportStatement.class, PyFromImportStatement.class);
        final StringBuilder buf = new StringBuilder("| ");
        if (elt != null) { // always? who knows :)
          if (elt instanceof PyFromImportStatement) { // from ... import ...
            buf.append("from ");
            PyReferenceExpression imp_src = ((PyFromImportStatement)elt).getImportSource();
            if (imp_src != null) {
              buf.append(PyPsiUtils.toPath(imp_src));
            }
            else {
              buf.append("<?>");
            }
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
        PyTargetExpression as_part = getAsNameElement();
        if (as_part != null) {
          buf.append(" as ").append(as_part.getName());
        }
        return buf.toString();
      }

      public Icon getIcon(final boolean open) {
        return null;
      }
    };
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyElement ret = getAsNameElement();
    if (ret == null) {
      final PyReferenceExpression importReference = getImportReferenceExpression();
      if (importReference != null) {
        ret = PyPsiUtils.getFirstQualifier(importReference);
      }
    }
    if (ret == null) {
      return EmptyIterable.getInstance();
    }
    return Collections.singleton(ret);
  }

  @NotNull
  public List<RatedResolveResult> multiResolveName(@NotNull final String name) {
    return getElementsNamed(name, true);
  }

  @Nullable
  @Override
  public PsiElement getElementNamed(String name, boolean resolveImportElement) {
    final List<RatedResolveResult> results = getElementsNamed(name, resolveImportElement);
    return results.isEmpty() ? null : RatedResolveResult.sorted(results).get(0).getElement();
  }

  @NotNull
  private List<RatedResolveResult> getElementsNamed(@NotNull String name, boolean resolveImportElement) {
    String asName = getAsName();
    if (asName != null) {
      if (!Comparing.equal(name, asName)) {
        return Collections.emptyList();
      }
      if (resolveImportElement) {
        return multiResolve();
      }
      return ResolveResultList.to(this);
    }
    else {
      final QualifiedName qName = getImportedQName();
      if (qName == null || qName.getComponentCount() == 0 || !qName.getComponents().get(0).equals(name)) {
        return Collections.emptyList();
      }
      if (qName.getComponentCount() == 1) {
        if (resolveImportElement) {
          return multiResolve();
        }
        return ResolveResultList.to(this);
      }
      return ResolveResultList.to(createImportedModule(name));
    }
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    final List<RatedResolveResult> results = multiResolve();
    return results.isEmpty() ? null : RatedResolveResult.sorted(results).get(0).getElement();
  }

  @NotNull
  @Override
  public List<RatedResolveResult> multiResolve() {
    final QualifiedName qName = getImportedQName();
    return qName == null ? Collections.emptyList() : ResolveImportUtil.multiResolveImportElement(this, qName);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyImportElement(this);
  }

  @Override
  public String toString() {
    final QualifiedName qName = getImportedQName();
    return String.format("%s:%s", super.toString(), qName != null ? qName : "null");
  }

  @Nullable
  private PsiElement createImportedModule(String name) {
    final PsiFile file = getContainingFile();
    if (file instanceof PyFile) {
      return new PyImportedModule(this, (PyFile)file, QualifiedName.fromComponents(name));
    }
    return null;
  }
}
