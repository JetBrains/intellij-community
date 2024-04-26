// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;


public class PyFromImportStatementImpl extends PyBaseElementImpl<PyFromImportStatementStub> implements PyFromImportStatement, PsiListLikeElement{
  public PyFromImportStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyFromImportStatementImpl(PyFromImportStatementStub stub) {
    this(stub, PyStubElementTypes.FROM_IMPORT_STATEMENT);
  }

  public PyFromImportStatementImpl(PyFromImportStatementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFromImportStatement(this);
  }

  @Override
  public QualifiedName getImportSourceQName() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      final QualifiedName qName = stub.getImportSourceQName();
      if (qName != null && qName.getComponentCount() == 0) {  // relative import only: from .. import the_name
        return null;
      }
      return qName;
    }

    return PyFromImportStatement.super.getImportSourceQName();
  }

  @Override
  public PyImportElement @NotNull [] getImportElements() {
    return getImportElements(PyElementTypes.IMPORT_ELEMENT, PyTokenTypes.IMPORT_KEYWORD);
  }

  final protected PyImportElement @NotNull [] getImportElements(
    @NotNull IElementType importElementType,
    @NotNull PyElementType importKeywordToken) {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(importElementType, count -> new PyImportElement[count]);
    }
    List<PyImportElement> result = new ArrayList<>();
    final ASTNode importKeyword = getNode().findChildByType(importKeywordToken);
    if (importKeyword != null) {
      for (ASTNode node = importKeyword.getTreeNext(); node != null; node = node.getTreeNext()) {
        if (node.getElementType() == importElementType) {
          result.add((PyImportElement)node.getPsi());
        }
      }
    }
    return result.toArray(new PyImportElement[0]);
  }

  @Override
  public int getRelativeLevel() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getRelativeLevel();
    }

    return PyFromImportStatement.super.getRelativeLevel();
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    // import is per-file
    if (place.getContainingFile() != getContainingFile()) {
      return true;
    }
    if (isStarImport()) {
      final List<PsiElement> targets = ResolveImportUtil.resolveFromImportStatementSource(this, getImportSourceQName());
      for (PsiElement target : targets) {
        final PsiElement importedFile = PyUtil.turnDirIntoInit(target);
        if (importedFile != null) {
          PsiScopeProcessor starImportableNamesProcessor = new DelegatingScopeProcessor(processor) {
            @Override
            public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
              if (element instanceof PyElement && importedFile instanceof PyFile &&
                  !PyUtil.isStarImportableFrom(StringUtil.notNullize(((PyElement)element).getName()), (PyFile)importedFile)) {
                return true;
              }
              return super.execute(element, state);
            }
          };
          if (!importedFile.processDeclarations(starImportableNamesProcessor, state, null, place)) {
            return false;
          }
        }
      }
    }
    else {
      PyImportElement[] importElements = getImportElements();
      for (PyImportElement element : importElements) {
        if (!processor.execute(element, state)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    boolean addingNewName = first == last &&
                            first.getElementType() == PyElementTypes.IMPORT_ELEMENT &&
                            (anchor == null || anchor.getElementType() == PyElementTypes.IMPORT_ELEMENT);
    if (!addingNewName) {
      return super.addInternal(first, last, anchor, before);
    }

    if (anchor == null) {
      final PyImportElement[] elements = getImportElements();
      if (elements.length != 0) {
        if (before) {
          anchor = elements[elements.length - 1].getNode();
          before = false;
        }
        else {
          anchor = elements[0].getNode();
          before = true;
        }
      }
    }

    // In an incomplete from import statement there is a special sentinel empty PyImportElement at the end
    if (anchor != null && anchor.getTextLength() == 0) {
      getNode().replaceChild(anchor, first);
      return first;
    }
    else {
      final ASTNode result = super.addInternal(first, last, anchor, before);
      if (anchor != null && anchor.getElementType() == PyElementTypes.IMPORT_ELEMENT &&
          result.getElementType() == PyElementTypes.IMPORT_ELEMENT) {
        ASTNode comma = PyElementGenerator.getInstance(getProject()).createComma();
        super.addInternal(comma, comma, before ? result : anchor, false);
      }
      return result;
    }
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (ArrayUtil.contains(child.getPsi(), getImportElements())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
    }
    super.deleteChildInternal(child);
  }

  @Override
  public @Nullable PyStarImportElement getStarImportElement() {
    return getStubOrPsiChild(PyStubElementTypes.STAR_IMPORT_ELEMENT);
  }

  @Override
  @Nullable
  public PsiFileSystemItem resolveImportSource() {
    return FluentIterable.from(resolveImportSourceCandidates()).filter(PsiFileSystemItem.class).first().orNull();
  }

  @NotNull
  @Override
  public List<PsiElement> resolveImportSourceCandidates() {
    final QualifiedName qName = getImportSourceQName();
    if (qName == null) {
      final int level = getRelativeLevel();
      if (level > 0) {
        final PsiDirectory upper = ResolveImportUtil.stepBackFrom(getContainingFile().getOriginalFile(), level);
        return ContainerUtil.createMaybeSingletonList(upper);
      }
    }
    return ResolveImportUtil.resolveFromImportStatementSource(this, qName);
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
    final QualifiedName importSourceQName = getImportSourceQName();
    if (importSourceQName != null && importSourceQName.endsWith(name)) {
      final PsiElement element = resolveImplicitSubModule();
      if (element != null) {
        return ResolveResultList.to(element);
      }
    }
    return Collections.emptyList();
  }

  /**
   * The statement 'from pkg1.m1 import ...' makes 'm1' available as a local name in the package 'pkg1'.
   *
   * http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
   */
  @Nullable
  private PsiElement resolveImplicitSubModule() {
    final QualifiedName importSourceQName = getImportSourceQName();
    if (importSourceQName != null) {
      final String name = importSourceQName.getLastComponent();
      final PsiFile file = getContainingFile();
      if (name != null && PyUtil.isPackage(file)) {
        final PsiElement resolvedImportSource = PyUtil.turnInitIntoDir(resolveImportSource());
        if (resolvedImportSource != null && resolvedImportSource.getParent() == file.getContainingDirectory()) {
          return resolvedImportSource;
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
