package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;

@TemplateInfo(
  templateName = "assert",
  description = "Creates assertion from boolean expression",
  example = "assert expr;")
public final class AssertStatementPostfixTemplate extends BooleanPostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PrefixExpressionContext context) {
    if (context.canBeStatement) {
      return new AssertLookupElement(context);
    }

    return null;
  }

  static final class AssertLookupElement extends StatementPostfixLookupElement<PsiAssertStatement> {
    public AssertLookupElement(@NotNull PrefixExpressionContext expression) {
      super("assert", expression);
    }

    @NotNull
    @Override
    protected PsiAssertStatement createNewStatement(@NotNull PsiElementFactory factory,
                                                    @NotNull PsiElement expression,
                                                    @NotNull PsiElement context) {
      PsiAssertStatement assertStatement = (PsiAssertStatement)factory.createStatementFromText("assert expr;", expression);

      PsiExpression condition = assertStatement.getAssertCondition();
      assert condition != null;
      condition.replace(expression);

      return assertStatement;
    }
  }
}