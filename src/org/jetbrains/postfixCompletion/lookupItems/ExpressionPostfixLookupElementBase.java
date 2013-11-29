package org.jetbrains.postfixCompletion.lookupItems;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;

public abstract class ExpressionPostfixLookupElementBase<T extends PsiExpression> extends PostfixLookupElementBase<T> {
  public ExpressionPostfixLookupElementBase(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  @Override
  @NotNull
  protected T handlePostfixInsert(@NotNull InsertionContext context, @NotNull PrefixExpressionContext expressionContext) {
    // get facade and factory while all elements are physical and valid
    Project project = expressionContext.expression.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory elementFactory = psiFacade.getElementFactory();

    // fix up expression before template expansion
    PrefixExpressionContext fixedContext = expressionContext.fixExpression();
    PsiElement expressionCopy = fixedContext.expression.copy();

    T newExpression = createNewExpression(
      elementFactory, expressionCopy, fixedContext.expression);

    @SuppressWarnings("unchecked")
    T replaced = (T)fixedContext.expression.replace(newExpression);

    return replaced;
  }

  @NotNull
  protected abstract T createNewExpression(@NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context);

  @Override
  protected void postProcess(@NotNull InsertionContext context, @NotNull T expression) {
    int offset = expression.getTextRange().getEndOffset();
    context.getEditor().getCaretModel().moveToOffset(offset);
  }
}