package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;

@TemplateInfo(
  templateName = "switch",
  description = "Produces switch over integral/enum/string values",
  example = "switch (expr)")
public final class SwitchStatementPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    for (PrefixExpressionContext expressionContext : context.expressions()) {
      if (!expressionContext.canBeStatement) continue;

      if (!context.executionContext.isForceMode) {
        PsiType expressionType = expressionContext.expressionType;
        if (expressionType == null) continue;
        if (!isSwitchCompatibleType(expressionType, expressionContext.expression)) continue;
      }

      return new ReturnLookupElement(expressionContext);
    }

    return null;
  }

  private static boolean isSwitchCompatibleType(@NotNull PsiType type, @NotNull PsiElement context) {
    // byte, short, char, int
    if (PsiType.INT.isAssignableFrom(type)) return true;

    if (type instanceof PsiClassType) { // enum
      PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null && psiClass.isEnum()) return true;
    }

    if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) { // string
      PsiFile containingFile = context.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        LanguageLevel level = ((PsiJavaFile)containingFile).getLanguageLevel();
        if (level.isAtLeast(LanguageLevel.JDK_1_7)) return true;
      }
    }

    return false;
  }

  static final class ReturnLookupElement extends StatementPostfixLookupElement<PsiSwitchStatement> {
    public ReturnLookupElement(@NotNull PrefixExpressionContext expression) {
      super("switch", expression);
    }

    @NotNull
    @Override
    protected PsiSwitchStatement createNewStatement(@NotNull PsiElementFactory factory,
                                                    @NotNull PsiElement expression,
                                                    @NotNull PsiElement context) {
      PsiSwitchStatement switchStatement = (PsiSwitchStatement)factory.createStatementFromText("switch (expr)", expression);
      PsiExpression condition = switchStatement.getExpression();
      assert condition != null;
      condition.replace(expression);
      return switchStatement;
    }

    @Override
    protected void postProcess(@NotNull InsertionContext context, @NotNull PsiSwitchStatement statement) {
      PsiJavaToken rParenth = statement.getRParenth();
      assert rParenth != null;
      int offset = rParenth.getTextRange().getEndOffset();
      context.getEditor().getCaretModel().moveToOffset(offset);
    }
  }
}
