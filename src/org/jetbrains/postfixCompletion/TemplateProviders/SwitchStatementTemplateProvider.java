package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.pom.java.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "switch",
  description = "Produces switch over integral/enum/string values",
  example = "switch (expr)")
public final class SwitchStatementTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(@NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    for (PrefixExpressionContext expressionContext : context.expressions()) {
      if (!expressionContext.canBeStatement) continue;

      if (!context.executionContext.isForceMode) {
        PsiType expressionType = expressionContext.expressionType;
        if (expressionType == null) continue;
        if (!isSwitchCompatibleType(expressionType, expressionContext.expression)) continue;
      }

      consumer.add(new ReturnLookupElement(expressionContext));
      break;
    }
  }

  private boolean isSwitchCompatibleType(@NotNull PsiType type, @NotNull PsiElement context) {
    // byte, short, char, int
    if (PsiType.INT.isAssignableFrom(type)) return true;

    if (type instanceof PsiClassType) { // enum
      PsiClass psiClass = ((PsiClassType) type).resolve();
      if (psiClass != null && psiClass.isEnum()) return true;
    }

    if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) { // string
      PsiFile containingFile = context.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        LanguageLevel level = ((PsiJavaFile) containingFile).getLanguageLevel();
        if (level.isAtLeast(LanguageLevel.JDK_1_7)) return true;
      }
    }

    return false;
  }

  static final class ReturnLookupElement extends StatementPostfixLookupElement<PsiSwitchStatement> {
    public ReturnLookupElement(@NotNull PrefixExpressionContext expression) {
      super("switch", expression);
    }

    @NotNull @Override protected PsiSwitchStatement createNewStatement(
        @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      PsiSwitchStatement switchStatement =
        (PsiSwitchStatement) factory.createStatementFromText("switch (expr)", expression);

      PsiExpression condition = switchStatement.getExpression();

      assert (condition != null) : "condition != null";
      condition.replace(expression);

      return switchStatement;
    }

    @Override protected void postProcess(@NotNull InsertionContext context, @NotNull PsiSwitchStatement statement) {
      PsiJavaToken rParenth = statement.getRParenth();
      assert (rParenth != null) : "rParenth != null";

      int offset = rParenth.getTextRange().getEndOffset();
      context.getEditor().getCaretModel().moveToOffset(offset);
    }
  }
}
