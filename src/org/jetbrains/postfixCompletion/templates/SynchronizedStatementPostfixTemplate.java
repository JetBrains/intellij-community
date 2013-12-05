package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

public class SynchronizedStatementPostfixTemplate extends PostfixTemplate {
  public SynchronizedStatementPostfixTemplate() {
    super("synchronized", "Produces synchronization statement", "synchronized (expr)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expression = getTopmostExpression(context);
    PsiElement parent = expression != null ? expression.getParent() : null;
    return parent instanceof PsiExpressionStatement && goodEnoughType(expression);
  }

  private static boolean goodEnoughType(@NotNull PsiExpression expression) {
    PsiType expressionType = expression.getType();
    return !(expressionType instanceof PsiPrimitiveType) && expressionType != null;
    // if (!expressionType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return null;  // todo: what does it mean?
  }

  // todo: very common code with switch, ideas?
  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    if (!(parent instanceof PsiExpressionStatement)) return;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    PsiSynchronizedStatement synchronizedStatement;

    Project project = context.getProject();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    synchronizedStatement = (PsiSynchronizedStatement)codeStyleManager
      .reformat(factory.createStatementFromText("synchronized (" + expr.getText() + "){\nst;\n}", context));
    synchronizedStatement = (PsiSynchronizedStatement)parent.replace(synchronizedStatement);

    // noinspection ConstantConditions
    PsiCodeBlock block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(synchronizedStatement.getBody());
    TextRange range = block.getStatements()[0].getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    editor.getCaretModel().moveToOffset(range.getStartOffset());
  }
}