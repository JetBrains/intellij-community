// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementPart;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyEvaluator;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public class PyFileStubBuilder extends DefaultStubBuilder {
  @Override
  protected @NotNull StubElement createStubForFile(@NotNull PsiFile file) {
    if (file instanceof PyFile) {
      return new PyFileStubImpl((PyFile)file);
    }

    return super.createStubForFile(file);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    PsiElement parentPsi = parent.getPsi();
    if (parentPsi instanceof PyIfStatement && PyUtil.isIfNameEqualsMain((PyIfStatement)parentPsi)) {
      return true;
    }
    if (parentPsi instanceof PyIfStatement ifStatement && node.getPsi() instanceof PyStatementPart part) {
      return isAlwaysUnreachable(ifStatement, part);
    }
    return false;
  }

  private static boolean isAlwaysUnreachable(@NotNull PyIfStatement ifStatement, @NotNull PyStatementPart part) {
    assert part.getParent() == ifStatement;

    for (PyIfPart ifPart : StreamEx.of(ifStatement.getIfPart()).append(ifStatement.getElifParts())) {
      if (ifPart == part) {
        break;
      }
      Boolean result = PyEvaluator.evaluateAsBooleanNoResolve(ifPart.getCondition());
      if (result == Boolean.TRUE) {
        return true;
      }
    }

    if (part instanceof PyIfPart ifPart) {
      Boolean result = PyEvaluator.evaluateAsBooleanNoResolve(ifPart.getCondition());
      if (result == Boolean.FALSE) {
        return true;
      }
    }

    return false;
  }
}
