
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

class SurroundWithDoWhileHandler implements SurroundStatementsHandler{
  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = manager.getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, false);
    if (statements.length == 0){
      return null;
    }

    String text = "do{\n}while(true);";
    PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)factory.createStatementFromText(text, null);
    doWhileStatement = (PsiDoWhileStatement)codeStyleManager.reformat(doWhileStatement);

    doWhileStatement = (PsiDoWhileStatement)container.addAfter(doWhileStatement, statements[statements.length - 1]);

    PsiCodeBlock bodyBlock = ((PsiBlockStatement)doWhileStatement.getBody()).getCodeBlock();
    bodyBlock.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    return doWhileStatement.getCondition().getTextRange();
  }
}