package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.RemoveTrailingSemicolonQuickFix;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStatement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

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
  public PyElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
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
