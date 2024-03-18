// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.ImmutableList;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiListLikeElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;


public class PyImportStatementImpl extends PyBaseElementImpl<PyImportStatementStub> implements PyImportStatement, PsiListLikeElement {
  public PyImportStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyImportStatementImpl(PyImportStatementStub stub) {
    this(stub, PyStubElementTypes.IMPORT_STATEMENT);
  }

  public PyImportStatementImpl(PyImportStatementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public PyImportElement @NotNull [] getImportElements() {
    final PyImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(PyElementTypes.IMPORT_ELEMENT, count -> new PyImportElement[count]);
    }
    return childrenToPsi(TokenSet.create(PyElementTypes.IMPORT_ELEMENT), new PyImportElement[0]);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyImportStatement(this);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (ArrayUtil.contains(child.getPsi(), getImportElements())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
    }
    super.deleteChildInternal(child);
  }

  @NotNull
  @Override
  public Iterable<PyElement> iterateNames() {
    final PyElement resolved = as(resolveImplicitSubModule(), PyElement.class);
    return resolved != null ? ImmutableList.of(resolved) : Collections.emptyList();
  }

  @NotNull
  @Override
  public List<RatedResolveResult> multiResolveName(@NotNull String name) {
    final PyImportElement[] elements = getImportElements();
    if (elements.length == 1) {
      final PyImportElement element = elements[0];
      final QualifiedName importedQName = element.getImportedQName();
      if (importedQName != null && importedQName.getComponentCount() > 1 && name.equals(importedQName.getLastComponent())) {
        return ResolveResultList.to(resolveImplicitSubModule());
      }
    }
    return Collections.emptyList();
  }

  /**
   * The statement 'import pkg1.m1' makes 'm1' available as a local name in the package 'pkg1'.
   *
   * http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
   */
  @Nullable
  private PsiElement resolveImplicitSubModule() {
    final PyImportElement[] elements = getImportElements();
    if (elements.length == 1) {
      final PyImportElement element = elements[0];
      final QualifiedName importedQName = element.getImportedQName();
      final PsiFile file = element.getContainingFile();
      if (file != null) {
        if (importedQName != null && importedQName.getComponentCount() > 1 && PyUtil.isPackage(file)) {
          final QualifiedName packageQName = importedQName.removeLastComponent();
          final PsiElement resolvedImport = PyUtil.turnDirIntoInit(ResolveImportUtil.resolveImportElement(element, packageQName));
          if (resolvedImport == file) {
            return element.resolve();
          }
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return Arrays.asList(getImportElements());
  }
}
