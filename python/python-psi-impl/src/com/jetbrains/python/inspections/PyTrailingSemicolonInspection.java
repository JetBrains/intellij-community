// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.RemoveTrailingSemicolonQuickFix;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyTrailingSemicolonInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyStatement(final @NotNull PyStatement statement) {
      ASTNode statementNode = statement.getNode();
      if (statementNode != null) {
        ASTNode[] nodes = statementNode.getChildren(TokenSet.create(PyTokenTypes.SEMICOLON));
        for (ASTNode node : nodes) {
          ASTNode nextNode = statementNode.getTreeNext();
          while ((nextNode instanceof PsiWhiteSpace) && (!nextNode.textContains('\n'))) {
            nextNode = nextNode.getTreeNext();
          }
          if (nextNode == null || nextNode.textContains('\n')) {
            if (ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                                     extension -> extension.ignoreTrailingSemicolon(statement))) {
              return;
            }
            registerProblem(node.getPsi(), PyPsiBundle.message("INSP.trailing.semicolon"), new RemoveTrailingSemicolonQuickFix());
          }
        }
      }
    }
  }
}
