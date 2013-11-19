package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

// todo: make it work on integers/Integers
// todo: check over String

@TemplateProvider(
  templateName = "fori",
  description = "Iterates over collection with index",
  example = "for (int i = 0; i < expr.length; i++)")
public class ForIndexedIterationTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {


    PrefixExpressionContext expression = context.innerExpression;
    if (!expression.canBeStatement) return;

    PsiType expressionType = expression.expressionType;

    String methodAccess = findSizeLikeMethod(expressionType, expression.expression);
    if (methodAccess == null && !context.executionContext.isForceMode) return;

    consumer.add(new ForIndexedLookupElement(expression, methodAccess));
  }

  @Nullable public static String findSizeLikeMethod(@Nullable PsiType psiType, @NotNull PsiElement accessContext) {
    // plain array types
    if (psiType instanceof PsiArrayType) {
      return ".length";
    }

    // custom collection types with size()/length()/count() methods
    if (psiType instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType) psiType).resolve();
      if (psiClass != null) {
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(accessContext.getProject());
        PsiClass containingType = PsiTreeUtil.getParentOfType(accessContext, PsiClass.class);
        PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();

        for (PsiMethod psiMethod : psiClass.getAllMethods()) {
          String methodName = psiMethod.getName();
          if (methodName.equals("size") || methodName.equals("length") || methodName.equals("count")) {
            if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) continue;
            if (psiMethod.getParameterList().getParametersCount() != 0) continue;

            PsiType returnType = psiMethod.getReturnType();
            if (!TypeConversionUtil.isNumericType(returnType)) continue;

            if (resolveHelper.isAccessible(psiMethod, accessContext, containingType)) {
              return "." + methodName + "()";
            }
          }
        }
      }
    }

    // any other numeric types
    if (TypeConversionUtil.isNumericType(psiType)) {
      return ""; // for (int i = 0; i < expr; i++)
    }

    return null;
  }

  private static final class ForIndexedLookupElement extends StatementPostfixLookupElement<PsiForStatement> {
    @NotNull private final String myMethodAccess;

    public ForIndexedLookupElement(@NotNull PrefixExpressionContext context, @NotNull String methodAccess) {
      super("fori", context);
      myMethodAccess = methodAccess;
    }

    @NotNull @Override protected PsiForStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      String template = "for(int i = 0; i < expr" + myMethodAccess + "; i++)";
      PsiForStatement forStatement = (PsiForStatement) factory.createStatementFromText(template, context);

      PsiBinaryExpression condition = (PsiBinaryExpression) forStatement.getCondition();
      assert (condition != null) : "condition != null";

      PsiExpression upperBound = condition.getROperand();

      if (upperBound instanceof PsiMethodCallExpression) { // expr.size()
        upperBound = ((PsiMethodCallExpression) upperBound).getMethodExpression();
      }

      if (upperBound instanceof PsiReferenceExpression) { // expr.length
        PsiReferenceExpression reference = (PsiReferenceExpression) upperBound;
        if (!"expr".equals(reference.getReferenceName())) {
          upperBound = (PsiExpression) reference.getQualifier();
        }
      }

      assert (upperBound != null) : "upperBound != null";
      upperBound.replace(expression);

      return forStatement;
    }

    @Override protected void postProcess(
      @NotNull final InsertionContext context, @NotNull final PsiForStatement forStatement) {
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(context.getProject());
      final SmartPsiElementPointer<PsiForStatement> statementPointer =
        pointerManager.createSmartPsiElementPointer(forStatement);

      final Runnable runnable = new Runnable() {
        @Override public void run() {
          PsiForStatement statement = statementPointer.getElement();
          if (statement == null) return;

          /*
          // create template for iteration expression
          TemplateBuilderImpl builder = new TemplateBuilderImpl(statement);
          PsiParameter iterationParameter = statement.getIterationParameter();

          // store pointer to iterated value
          PsiExpression iteratedValue = statement.getIteratedValue();
          assert iteratedValue != null : "iteratedValue != null";
          final SmartPsiElementPointer<PsiExpression> valuePointer =
            pointerManager.createSmartPsiElementPointer(iteratedValue);

          // use standard macro, pass parameter expression with expression to iterate
          MacroCallNode iterableTypeExpression = new MacroCallNode(new IterableComponentTypeMacro());
          iterableTypeExpression.addParameter(new PsiPointerExpression(valuePointer));

          MacroCallNode nameExpression = new MacroCallNode(new SuggestVariableNameMacro());

          // setup placeholders and final position
          builder.replaceElement(iterationParameter.getTypeElement(), iterableTypeExpression, false);
          builder.replaceElement(iterationParameter.getNameIdentifier(), nameExpression, true);
          builder.setEndVariableAfter(statement.getRParenth());

          // todo: braces insertion?

          // create inline template and place caret before statement
          Template template = builder.buildInlineTemplate();

          Editor editor = context.getEditor();
          CaretModel caretModel = editor.getCaretModel();
          caretModel.moveToOffset(statement.getTextRange().getStartOffset());

          TemplateManager manager = TemplateManager.getInstance(context.getProject());
          manager.startTemplate(editor, template);
          */
        }
      };

      context.setLaterRunnable(new Runnable() {
        @Override public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override public void run() {
              CommandProcessor.getInstance().runUndoTransparentAction(runnable);
            }
          });
        }
      });
    }

    private static final class PsiPointerExpression extends Expression {
      @NotNull private final SmartPsiElementPointer<PsiExpression> valuePointer;

      public PsiPointerExpression(@NotNull SmartPsiElementPointer<PsiExpression> valuePointer) {
        this.valuePointer = valuePointer;
      }

      @Nullable @Override public Result calculateResult(ExpressionContext expressionContext) {
        return new PsiElementResult(valuePointer.getElement());
      }

      @Nullable @Override public Result calculateQuickResult(ExpressionContext expressionContext) {
        return calculateResult(expressionContext);
      }

      @Nullable @Override public LookupElement[] calculateLookupItems(ExpressionContext expressionContext) {
        return LookupElement.EMPTY_ARRAY;
      }
    }
  }
}