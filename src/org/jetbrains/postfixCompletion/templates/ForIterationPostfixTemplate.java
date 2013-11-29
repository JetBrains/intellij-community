package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.SuggestIndexNameMacro;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;

public abstract class ForIterationPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext expression = context.innerExpression();
    if (!expression.canBeStatement) return null;

    PsiType expressionType = expression.expressionType;

    Pair<String, String> info = findSizeLikeMethod(expressionType, expression.expression);
    if (info == null) {
      if (!context.executionContext.isForceMode) {
        return null;
      }
      else {
        info = Pair.create("", "int");
      }
    }

    return createIterationLookupElement(expression, info.second, info.first);
  }

  @NotNull
  protected abstract ForLookupElementBase createIterationLookupElement(@NotNull PrefixExpressionContext expression,
                                                                       @NotNull String indexVarType,
                                                                       @NotNull String sizeAccessSuffix);

  protected abstract static class ForLookupElementBase extends StatementPostfixLookupElement<PsiForStatement> {
    @NotNull private final String myTemplate;

    public ForLookupElementBase(@NotNull String lookupString, @NotNull PrefixExpressionContext context, @NotNull String template) {
      super(lookupString, context);
      myTemplate = template;
    }

    @NotNull
    @Override
    protected PsiForStatement createNewStatement(@NotNull PsiElementFactory factory,
                                                 @NotNull PsiElement expression,
                                                 @NotNull PsiElement context) {
      PsiForStatement forStatement = (PsiForStatement)factory.createStatementFromText(myTemplate, context);
      PsiExpression upperBound = findBoundExpression(forStatement);
      upperBound.replace(expression);
      return forStatement;
    }

    @NotNull
    protected abstract PsiExpression findBoundExpression(@NotNull PsiForStatement forStatement);

    @NotNull
    protected PsiExpression unwrapExpression(PsiExpression boundExpression) {
      assert boundExpression != null;

      if (boundExpression instanceof PsiMethodCallExpression) { // expr.size()
        boundExpression = ((PsiMethodCallExpression)boundExpression).getMethodExpression();
      }

      if (boundExpression instanceof PsiReferenceExpression) { // expr.length
        PsiReferenceExpression reference = (PsiReferenceExpression)boundExpression;
        if (!"expr".equals(reference.getReferenceName())) {
          boundExpression = (PsiExpression)reference.getQualifier();
        }
      }

      assert boundExpression != null;
      return boundExpression;
    }

    @Override
    protected void postProcess(@NotNull final InsertionContext context, @NotNull PsiForStatement forStatement) {
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(context.getProject());
      final SmartPsiElementPointer<PsiForStatement> statementPointer = pointerManager.createSmartPsiElementPointer(forStatement);

      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          PsiForStatement statement = statementPointer.getElement();
          if (statement == null) return;

          // create template for for statement
          TemplateBuilderImpl builder = new TemplateBuilderImpl(statement);
          buildTemplate(builder, statement);

          // todo: braces insertion?

          // create inline template and place caret before statement
          Template template = builder.buildInlineTemplate();

          Editor editor = context.getEditor();
          CaretModel caretModel = editor.getCaretModel();
          caretModel.moveToOffset(statement.getTextRange().getStartOffset());

          TemplateManager manager = TemplateManager.getInstance(context.getProject());
          manager.startTemplate(editor, template);
        }
      };

      context.setLaterRunnable(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              CommandProcessor.getInstance().runUndoTransparentAction(runnable);
            }
          });
        }
      });
    }

    protected void buildTemplate(@NotNull TemplateBuilderImpl builder, @NotNull PsiForStatement forStatement) {
      PsiDeclarationStatement initialization = (PsiDeclarationStatement)forStatement.getInitialization();
      assert initialization != null;

      PsiLocalVariable indexVariable = (PsiLocalVariable)initialization.getDeclaredElements()[0];

      PsiBinaryExpression condition = (PsiBinaryExpression)forStatement.getCondition();
      assert condition != null;

      PsiReferenceExpression indexRef1 = (PsiReferenceExpression)condition.getLOperand();

      PsiExpressionStatement updateStatement = (PsiExpressionStatement)forStatement.getUpdate();
      assert updateStatement != null;

      PsiPostfixExpression increment = (PsiPostfixExpression)updateStatement.getExpression();
      PsiReferenceExpression indexRef2 = (PsiReferenceExpression)increment.getOperand();

      // use standard macro, pass parameter expression with expression to iterate
      MacroCallNode nameExpression = new MacroCallNode(new SuggestIndexNameMacro());

      // setup placeholders and final position
      builder.replaceElement(indexVariable.getNameIdentifier(), "INDEX", nameExpression, true);
      builder.replaceElement(indexRef1.getReferenceNameElement(), "FOO1", "INDEX", false);
      builder.replaceElement(indexRef2.getReferenceNameElement(), "FOO1", "INDEX", false);

      builder.setEndVariableAfter(forStatement.getRParenth());
    }
  }

  @Nullable
  public static Pair<String, String> findSizeLikeMethod(@Nullable PsiType psiType, @NotNull PsiElement accessContext) {
    // plain array types
    if (psiType instanceof PsiArrayType) {
      return Pair.create(".length", "int");
    }

    // custom collection types with size()/length()/count() methods
    if (psiType instanceof PsiClassType) {
      PsiClass psiClass = ((PsiClassType)psiType).resolve();
      if (psiClass == null) return null;

      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(accessContext.getProject());
      PsiClass containingType = PsiTreeUtil.getParentOfType(accessContext, PsiClass.class);
      PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();

      for (PsiMethod psiMethod : psiClass.getAllMethods()) {
        String methodName = psiMethod.getName();
        if (methodName.equals("size") || methodName.equals("length") || methodName.equals("count")) {
          if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) continue;
          if (psiMethod.getParameterList().getParametersCount() != 0) continue;

          PsiType returnType = psiMethod.getReturnType();
          if (returnType == null) continue;

          String integralType = isIntegralType(returnType);
          if (integralType == null) continue;

          if (resolveHelper.isAccessible(psiMethod, accessContext, containingType)) {
            return Pair.create("." + methodName + "()", integralType);
          }
        }
      }
    }

    // any other numeric types
    if (psiType != null) {
      String integralType = isIntegralType(psiType);
      if (integralType != null) { // for (int i = 0; i < expr; i++)
        return Pair.create("", integralType);
      }
    }

    return null;
  }

  @Nullable
  private static String isIntegralType(@NotNull PsiType psiType) {
    // note: order is important
    if (PsiType.BYTE.isAssignableFrom(psiType)) return "byte";
    if (PsiType.SHORT.isAssignableFrom(psiType)) return "short";
    if (PsiType.INT.isAssignableFrom(psiType)) return "int";
    if (PsiType.LONG.isAssignableFrom(psiType)) return "long";

    return null;
  }
}