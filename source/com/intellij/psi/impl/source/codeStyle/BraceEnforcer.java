package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class BraceEnforcer extends PsiRecursiveElementVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.BraceEnforcer");
  private CodeStyleSettings mySettings;

  public BraceEnforcer(CodeStyleSettings settings) {
    mySettings = settings;
  }


  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitElement(expression);
  }

  public void visitIfStatement(PsiIfStatement statement) {
    super.visitIfStatement(statement);
    processStatement(statement, statement.getThenBranch(), mySettings.IF_BRACE_FORCE);
    final PsiStatement elseBranch = statement.getElseBranch();
    if (!(elseBranch instanceof PsiIfStatement) || !mySettings.SPECIAL_ELSE_IF_TREATMENT) {
      processStatement(statement, elseBranch, mySettings.IF_BRACE_FORCE);
    }
  }

  public void visitForStatement(PsiForStatement statement) {
    super.visitForStatement(statement);
    processStatement(statement, statement.getBody(), mySettings.FOR_BRACE_FORCE);
  }

  public void visitForeachStatement(PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    processStatement(statement, statement.getBody(), mySettings.FOR_BRACE_FORCE);
  }

  public void visitWhileStatement(PsiWhileStatement statement) {
    super.visitWhileStatement(statement);
    processStatement(statement, statement.getBody(), mySettings.WHILE_BRACE_FORCE);
  }

  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    super.visitDoWhileStatement(statement);
    processStatement(statement, statement.getBody(), mySettings.DOWHILE_BRACE_FORCE);
  }

  private void processStatement(PsiStatement statement, PsiStatement blockCandidate, int options) {
    if (blockCandidate instanceof PsiBlockStatement || blockCandidate == null) return;
    if (options == CodeStyleSettings.FORCE_BRACES_ALWAYS ||
        options == CodeStyleSettings.FORCE_BRACES_IF_MULTILINE && isMultiline(statement)) {
      replaceWithBlock(statement, blockCandidate);
    }
  }

  private static void replaceWithBlock(PsiStatement statement, PsiStatement blockCandidate) {
    final PsiElementFactory factory = statement.getManager().getElementFactory();
    String oldText = blockCandidate.getText();
    StringBuffer buf = new StringBuffer(oldText.length() + 3);
    buf.append('{');
    buf.append(oldText);
    buf.append("\n}");
    try {
      CodeEditUtil.replaceChild((CompositeElement)SourceTreeToPsiMap.psiElementToTree(statement),
                                SourceTreeToPsiMap.psiElementToTree(blockCandidate),
                                SourceTreeToPsiMap.psiElementToTree(factory.createStatementFromText(buf.toString(), null)));
      CodeStyleManager.getInstance(statement.getProject()).reformat(statement);
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static boolean isMultiline(PsiStatement statement) {
    return statement.getText().indexOf('\n') > 0;
  }

  public PsiElement process(PsiElement formatted) {
    formatted.accept(this);
    return formatted;
  }

  public PsiElement process(PsiElement formatted, int startOffset, int endOffset) {
    formatted.accept(this);
    return formatted;
  }
}
