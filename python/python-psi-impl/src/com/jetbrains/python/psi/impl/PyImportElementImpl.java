// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyImportElementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The "import foo" or "import foo as bar" parts.
 */
public class PyImportElementImpl extends PyBaseElementImpl<PyImportElementStub> implements PyImportElement {
  public PyImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyImportElementImpl(PyImportElementStub stub) {
    this(stub, PyStubElementTypes.IMPORT_ELEMENT);
  }

  public PyImportElementImpl(PyImportElementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public QualifiedName getImportedQName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      return stub.getImportedQName();
    }
    return PyImportElement.super.getImportedQName();
  }

  @Override
  public String getAsName() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      final String asName = stub.getAsName();
      return StringUtil.isEmpty(asName) ? null : asName;
    }
    return PyImportElement.super.getAsName();
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
      return null; // we might have not found any names
    }
    else {
      return PyImportElement.super.getVisibleName();
    }
  }

  @Override
  @Nullable
  public PyStatement getContainingImportStatement() {
    final PyImportElementStub stub = getStub();
    if (stub != null) {
      PsiElement parent = stub.getParentStub().getPsi();
      return parent instanceof PyStatement ? (PyStatement)parent : null;
    }
    else {
      return PyImportElement.super.getContainingImportStatement();
    }
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

      @Override
      public String getPresentableText() {
        return getRefName("<none>");
      }

      @Override
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

      @Override
      public Icon getIcon(final boolean open) {
        return null;
      }
    };
  }

  @Override
  public String getName() {
    return getVisibleName();
  }

  @Override
  @NotNull
  public Iterable<PyElement> iterateNames() {
    final String visibleName = getVisibleName();
    return visibleName != null ? Collections.singletonList(this) : Collections.emptyList();
  }

  @Override
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
      if (!Objects.equals(name, asName)) {
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
