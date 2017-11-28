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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyFromImportStatementImpl extends PyBaseElementImpl<PyFromImportStatementStub> implements PyFromImportStatement{
  public PyFromImportStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyFromImportStatementImpl(PyFromImportStatementStub stub) {
    this(stub, PyElementTypes.FROM_IMPORT_STATEMENT);
  }

  public PyFromImportStatementImpl(PyFromImportStatementStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFromImportStatement(this);
  }

  public boolean isStarImport() {
    return getStarImportElement() != null;
  }

  @Nullable
  public PyReferenceExpression getImportSource() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getReferenceExpressionTokens(), 0);
  }

  public QualifiedName getImportSourceQName() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      final QualifiedName qName = stub.getImportSourceQName();
      if (qName != null && qName.getComponentCount() == 0) {  // relative import only: from .. import the_name
        return null;
      }
      return qName;
    }

    final PyReferenceExpression importSource = getImportSource();
    if (importSource == null) {
      return null;
    }
    return importSource.asQualifiedName();
  }

  @NotNull
  public PyImportElement[] getImportElements() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(PyElementTypes.IMPORT_ELEMENT, count -> new PyImportElement[count]);
    }
    List<PyImportElement> result = new ArrayList<>();
    final ASTNode importKeyword = getNode().findChildByType(PyTokenTypes.IMPORT_KEYWORD);
    if (importKeyword != null) {
      for (ASTNode node = importKeyword.getTreeNext(); node != null; node = node.getTreeNext()) {
        if (node.getElementType() == PyElementTypes.IMPORT_ELEMENT) {
          result.add((PyImportElement)node.getPsi());
        }
      }
    }
    return result.toArray(new PyImportElement[result.size()]);
  }

  @Nullable
  public PyStarImportElement getStarImportElement() {
    return getStubOrPsiChild(PyElementTypes.STAR_IMPORT_ELEMENT);
  }

  public int getRelativeLevel() {
    final PyFromImportStatementStub stub = getStub();
    if (stub != null) {
      return stub.getRelativeLevel();
    }

    int result = 0;
    ASTNode seeker = getNode().getFirstChildNode();
    while (seeker != null && (seeker.getElementType() == PyTokenTypes.FROM_KEYWORD || seeker.getElementType() == TokenType.WHITE_SPACE)) {
      seeker = seeker.getTreeNext();
    }
    while (seeker != null && seeker.getElementType() == PyTokenTypes.DOT) {
      result++;
      seeker = seeker.getTreeNext();
    }
    return result;
  }

  public boolean isFromFuture() {
    final QualifiedName qName = getImportSourceQName();
    return qName != null && qName.matches(PyNames.FUTURE_MODULE);
  }

  @Override
  public PsiElement getLeftParen() {
    return findChildByType(PyTokenTypes.LPAR);
  }

  @Override
  public PsiElement getRightParen() {
    return findChildByType(PyTokenTypes.RPAR);
  }

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
    if (anchor == null) {
      // adding last element; the import may be "from ... import (...)", must get before the last ")"
      PsiElement lastChild = getLastChild();
      if (lastChild != null) {
        while (lastChild instanceof PsiComment) {
          lastChild = lastChild.getPrevSibling();
          anchor = lastChild.getNode();
        }
        ASTNode rpar_node = lastChild.getNode();
        if (rpar_node != null && rpar_node.getElementType() == PyTokenTypes.RPAR) anchor = rpar_node;
      }
    }
    final ASTNode result = super.addInternal(first, last, anchor, before);
    ASTNode prevNode = result;
    do {
      prevNode = prevNode.getTreePrev();
    }
    while (prevNode != null && prevNode.getElementType() == TokenType.WHITE_SPACE);

    if (prevNode != null && prevNode.getElementType() == PyElementTypes.IMPORT_ELEMENT &&
        result.getElementType() == PyElementTypes.IMPORT_ELEMENT) {
      ASTNode comma = PyElementGenerator.getInstance(getProject()).createComma();
      super.addInternal(comma, comma, prevNode, false);
    }

    return result;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (ArrayUtil.contains(child.getPsi(), getImportElements())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
    }
    super.deleteChildInternal(child);
  }

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
        return upper == null ? Collections.emptyList() : Collections.singletonList(upper);
      }
    }
    return ResolveImportUtil.resolveFromImportStatementSource(this, qName);
  }

  @NotNull
  @Override
  public List<String> getFullyQualifiedObjectNames() {
    final QualifiedName source = getImportSourceQName();

    final String prefix = (source != null) ? (source.join(".") + '.') : "";

    final List<String> unqualifiedNames = PyImportStatementImpl.getImportElementNames(getImportElements());

    final List<String> result = new ArrayList<>(unqualifiedNames.size());

    for (final String unqualifiedName : unqualifiedNames) {
      result.add(prefix + unqualifiedName);
    }
    return result;
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
}
