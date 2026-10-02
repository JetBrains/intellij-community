// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyStubElementTypes;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

@ApiStatus.Internal
public class PyStarImportElementImpl extends PyBaseElementImpl<PyStarImportElementStub> implements PyStarImportElement {
  public PyStarImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyStarImportElementImpl(final PyStarImportElementStub stub) {
    super(stub, PyStubElementTypes.STAR_IMPORT_ELEMENT);
  }

  @Override
  public @NotNull Iterable<PyElement> iterateNames() {
    if (getParent() instanceof PyFromImportStatement fromImportStatement) {
      return StreamEx.of(fromImportStatement.resolveImportSourceCandidates())
        .distinct()
        .map(PyUtil::turnDirIntoInit)
        .select(PyFile.class)
        .flatMap(file -> StreamEx.of(file.iterateNames().iterator())
          .filter(e -> {
            String name = e.getName();
            return name != null && PyUtil.isStarImportableFrom(name, file);
          }))
        .toImmutableList();
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<RatedResolveResult> multiResolveName(@NotNull String name) {
    return PyUtil.getParameterizedCachedValue(this, name, this::calculateMultiResolveName);
  }

  private @NotNull List<RatedResolveResult> calculateMultiResolveName(@NotNull String name) {
    final PsiElement parent = getParentByStub();
    if (parent instanceof PyFromImportStatement fromImportStatement) {
      final List<PsiElement> importedFiles = fromImportStatement.resolveImportSourceCandidates();
      for (PsiElement importedFile : new HashSet<>(importedFiles)) { // resolver gives lots of duplicates
        final PyFile sourceFile = as(PyUtil.turnDirIntoInit(importedFile), PyFile.class);
        if (sourceFile != null && PyUtil.isStarImportableFrom(name, sourceFile)) {
          final PyModuleType moduleType = new PyModuleType(sourceFile);
          final var context = TypeEvalContext.codeInsightFallback(sourceFile.getProject());
          final List<? extends RatedResolveResult> results = moduleType.resolveMember(name, null, AccessDirection.READ,
                                                                                      PyResolveContext.defaultContext(context));
          if (!ContainerUtil.isEmpty(results)) {
            return Lists.newArrayList(results);
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {

      private String getName() {
        PyFromImportStatement elt = PsiTreeUtil.getParentOfType(PyStarImportElementImpl.this, PyFromImportStatement.class);
        if (elt != null) { // always? who knows :)
          PyReferenceExpression imp_src = elt.getImportSource();
          if (imp_src != null) {
            return PyPsiUtils.toPath(imp_src);
          }
        }
        return "<?>";
      }

      @Override
      public String getPresentableText() {
        return getName();
      }

      @Override
      public String getLocationString() {
        return "| " + "from " + getName() + " import *";
      }

      @Override
      public Icon getIcon(final boolean open) {
        return null;
      }
    };
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStarImportElement(this);
  }
}
