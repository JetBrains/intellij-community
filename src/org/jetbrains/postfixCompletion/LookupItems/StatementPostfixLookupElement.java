package org.jetbrains.postfixCompletion.lookupItems;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;

public abstract class StatementPostfixLookupElement<TStatement extends PsiStatement>
  extends PostfixLookupElement<TStatement> {

  public StatementPostfixLookupElement(
    @NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    super(lookupString, context);
  }

  @Override @NotNull protected TStatement handlePostfixInsert(
    @NotNull InsertionContext context, @NotNull PrefixExpressionContext expressionContext) {
    // get facade and factory while all elements are physical and valid
    Project project = expressionContext.expression.getProject();
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory elementFactory = psiFacade.getElementFactory();

    // fix up expression before template expansion
    PrefixExpressionContext fixedContext = expressionContext.fixExpression();

    // get target statement to replace
    PsiStatement targetStatement = fixedContext.getContainingStatement();
    assert (targetStatement != null) : "targetStatement != null";

    PsiElement expressionCopy = fixedContext.expression.copy();
    TStatement newStatement = createNewStatement(elementFactory, expressionCopy, fixedContext.expression);

    //noinspection unchecked
    return (TStatement) targetStatement.replace(newStatement);
  }

  @NotNull protected abstract TStatement createNewStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context);

  @Override protected void postProcess(
    @NotNull InsertionContext context, @NotNull TStatement statement) {
    int offset = statement.getTextRange().getEndOffset();
    context.getEditor().getCaretModel().moveToOffset(offset);
  }
}