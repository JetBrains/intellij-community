package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

@TemplateProvider(
  templateName = "fori",
  description = "Iterates over collection with index",
  example = "for (int i = 0; i < expr.length; i++)")
public class ForIndexedIterationTemplateProvider extends ForIterationTemplateProviderBase {
  @Override @NotNull protected ForIndexedLookupElement createIterationLookupElement(
    @NotNull PrefixExpressionContext expression, @NotNull String indexVarType, @NotNull String sizeAccessSuffix) {

    return new ForIndexedLookupElement(expression, indexVarType, sizeAccessSuffix);
  }

  private static final class ForIndexedLookupElement extends ForLookupElementBase {
    public ForIndexedLookupElement(
        @NotNull PrefixExpressionContext context,
        @NotNull String indexVariableType, @NotNull String sizeAccessSuffix) {
      super("fori", context,
        "for(" + indexVariableType + " i = 0; i < expr" + sizeAccessSuffix + "; i++)");
    }

    @NotNull protected PsiExpression findBoundExpression(@NotNull PsiForStatement forStatement) {
      PsiBinaryExpression condition = (PsiBinaryExpression) forStatement.getCondition();
      assert (condition != null) : "condition != null";

      PsiExpression boundExpression = condition.getROperand();
      boundExpression = unwrapExpression(boundExpression);

      return boundExpression;
    }

    @Override protected void buildTemplate(
        @NotNull TemplateBuilderImpl builder, @NotNull PsiForStatement forStatement) {
      PsiDeclarationStatement initialization = (PsiDeclarationStatement) forStatement.getInitialization();
      assert (initialization != null) : "initialization != null";

      PsiLocalVariable indexVariable = (PsiLocalVariable) initialization.getDeclaredElements()[0];

      PsiBinaryExpression condition = (PsiBinaryExpression) forStatement.getCondition();
      assert (condition != null) : "condition != null";

      PsiReferenceExpression indexRef1 = (PsiReferenceExpression) condition.getLOperand();

      PsiExpressionStatement updateStatement = (PsiExpressionStatement) forStatement.getUpdate();
      assert (updateStatement != null) : "updateStatement != null";

      PsiPostfixExpression increment = (PsiPostfixExpression) updateStatement.getExpression();
      PsiReferenceExpression indexRef2 = (PsiReferenceExpression) increment.getOperand();

      // use standard macro, pass parameter expression with expression to iterate
      MacroCallNode nameExpression = new MacroCallNode(new SuggestIndexNameMacro());

      // setup placeholders and final position
      builder.replaceElement(indexVariable.getNameIdentifier(), "INDEX", nameExpression, true);
      builder.replaceElement(indexRef1.getReferenceNameElement(), "FOO1", "INDEX", false);
      builder.replaceElement(indexRef2.getReferenceNameElement(), "FOO1", "INDEX", false);

      builder.setEndVariableAfter(forStatement.getRParenth());
    }
  }
}