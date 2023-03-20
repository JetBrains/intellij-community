/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

public class PyStarImportElementImpl extends PyBaseElementImpl<PyStarImportElementStub> implements PyStarImportElement {
  public PyStarImportElementImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyStarImportElementImpl(final PyStarImportElementStub stub) {
    super(stub, PyElementTypes.STAR_IMPORT_ELEMENT);
  }

  @Override
  @NotNull
  public Iterable<PyElement> iterateNames() {
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
  @NotNull
  public List<RatedResolveResult> multiResolveName(@NotNull String name) {
    return PyUtil.getParameterizedCachedValue(this, name, this::calculateMultiResolveName);
  }

  @NotNull
  private List<RatedResolveResult> calculateMultiResolveName(@NotNull String name) {
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
