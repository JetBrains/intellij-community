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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.inspections.quickfix.RemoveTrailingSemicolonQuickFix;
import com.jetbrains.python.psi.PyStatement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyTrailingSemicolonInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.trailing.semicolon");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyStatement(final PyStatement statement) {
      ASTNode statementNode = statement.getNode();
      if (statementNode != null) {
        ASTNode[] nodes = statementNode.getChildren(TokenSet.create(PyTokenTypes.SEMICOLON));
        for (ASTNode node : nodes) {
          ASTNode nextNode = statementNode.getTreeNext();
          while ((nextNode instanceof PsiWhiteSpace) && (!nextNode.textContains('\n'))) {
            nextNode = nextNode.getTreeNext();
          }
          if (nextNode == null || nextNode.textContains('\n')) {
            registerProblem(node.getPsi(), "Trailing semicolon in the statement", new RemoveTrailingSemicolonQuickFix());
          }
        }
      }
    }
  }
}
