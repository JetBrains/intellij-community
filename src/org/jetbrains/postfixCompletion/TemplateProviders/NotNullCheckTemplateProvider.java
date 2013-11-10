package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

@TemplateProvider(
  templateName = "notnull",
  description = "Checks expression to be not-null",
  example = "if (expr != null)")
public class NotNullCheckTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull PostfixTemplateAcceptanceContext context, @NotNull List<LookupElement> consumer) {

    PrefixExpressionContext expression = context.outerExpression;

    if (expression.referencedElement instanceof PsiClass) return;
    if (expression.referencedElement instanceof PsiPackage) return;
    if (!expression.canBeStatement) return;

    PsiType expressionType = expression.expressionType;
    if (expressionType != null && !context.isForceMode) {
      if (expressionType instanceof PsiPrimitiveType) return;
    }

    consumer.add(new CheckNotNullLookupElement(expression));
  }

  private static final class CheckNotNullLookupElement extends NullCheckLookupElementBase {
    public CheckNotNullLookupElement(@NotNull PrefixExpressionContext context) {
      super("notnull", context);
    }

    @Override public Set<String> getAllLookupStrings() {
      Set<String> strings = super.getAllLookupStrings();
      strings.add("notNull");
      strings.add("notNull ");

      return strings;
    }

    @Override protected String getTemplate() {
      return "if(expr!=null)";
    }
  }
}