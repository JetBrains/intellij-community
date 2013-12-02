package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.unwrap.ScopeHighlighter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.util.CommonUtils;
import org.jetbrains.postfixCompletion.util.JavaSurroundersProxy;

import java.util.List;

public final class CastExpressionPostfixTemplate extends PostfixTemplate {
  public CastExpressionPostfixTemplate() {
    super("cast");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context) {
    return PsiTreeUtil.getParentOfType(context, PsiExpression.class) != null;
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull final Editor editor) {
    List<PsiExpression> expressions =
      IntroduceVariableBase.collectExpressions(context.getContainingFile(), editor, editor.getCaretModel().getOffset());

    if (expressions.isEmpty()) {
      CommonUtils.showErrorHint(context.getProject(), editor);
    }
    else if (expressions.size() == 1) {
      doIt(editor, expressions.get(0));
    }
    else {

    IntroduceTargetChooser.showChooser(editor, expressions,
                                       new Pass<PsiExpression>() {
                                         public void pass(@NotNull PsiExpression e) {
                                           doIt(editor, e);
                                         }
                                       },
                                       new PsiExpressionTrimRenderer.RenderFunction(),
                                       "Expressions", 0, ScopeHighlighter.NATURAL_RANGER);
    }
  }

  private static void doIt(@NotNull final Editor editor, @NotNull final PsiExpression expression) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        JavaSurroundersProxy.cast(expression.getProject(), editor, expression);
      }
    });
  }
}