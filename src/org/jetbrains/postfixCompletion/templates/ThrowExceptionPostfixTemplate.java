package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;
import org.jetbrains.postfixCompletion.util.CommonUtils;

import static org.jetbrains.postfixCompletion.util.CommonUtils.CtorAccessibility;

@TemplateInfo(
  templateName = "throw",
  description = "Throws expression of 'Throwable' type",
  example = "throw expr;",
  worksOnTypes = true)
public final class ThrowExceptionPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext expression = context.outerExpression();
    if (!expression.canBeStatement) return null;

    CtorAccessibility accessibility = CtorAccessibility.NotAccessible;
    PsiType expressionType = expression.expressionType;
    PsiClass throwableClass = null;

    if (expressionType instanceof PsiClassType) {
      throwableClass = ((PsiClassType)expressionType).resolve();
    }
    else {
      PsiElement referencedElement = expression.referencedElement;
      if (referencedElement instanceof PsiClass) {
        throwableClass = (PsiClass)referencedElement;
      }
    }

    if (!context.executionContext.isForceMode) {
      if (throwableClass == null) return null;
      if (expressionType == null) {
        accessibility = CommonUtils.isTypeCanBeInstantiatedWithNew(throwableClass, expression.expression);
        if (accessibility == CtorAccessibility.NotAccessible) return null;

        String fqnName = throwableClass.getQualifiedName();
        if (fqnName == null) return null;

        expressionType = JavaPsiFacade
          .getElementFactory(expression.expression.getProject())
          .createTypeByFQClassName(fqnName, throwableClass.getResolveScope());
      }

      if (!InheritanceUtil.isInheritor(expressionType, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return null;
      }
    }

    PsiClass psiClass = (expression.referencedElement == throwableClass) ? throwableClass : null;
    return new ThrowStatementLookupElement(expression, psiClass, accessibility);
  }
  
  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    throw new UnsupportedOperationException("Implement me please");
  }

  private static class ThrowStatementLookupElement extends StatementPostfixLookupElement<PsiThrowStatement> {
    @Nullable private final PsiClass myThrowableClass;
    @NotNull private final CtorAccessibility myAccessibility;
    private final boolean myTypeRequiresRefinement;

    public ThrowStatementLookupElement(@NotNull PrefixExpressionContext context,
                                       @Nullable PsiClass throwableClass,
                                       @NotNull CtorAccessibility accessibility) {
      super("throw", context);
      myThrowableClass = throwableClass;
      myAccessibility = accessibility;
      myTypeRequiresRefinement = (throwableClass != null) && CommonUtils.isTypeRequiresRefinement(throwableClass);
    }

    @NotNull
    @Override
    protected PsiThrowStatement createNewStatement(@NotNull PsiElementFactory factory,
                                                   @NotNull PsiElement expression,
                                                   @NotNull PsiElement context) {
      PsiExpression throwableValue;
      if (myThrowableClass == null) {
        throwableValue = (PsiExpression)expression;
      }
      else {
        String template = "new Throwable()";
        if (myTypeRequiresRefinement) template += "{}";

        PsiNewExpression newExpression = (PsiNewExpression)factory.createExpressionFromText(template, context);
        PsiJavaCodeReferenceElement typeReference = newExpression.getClassOrAnonymousClassReference();
        assert typeReference != null;

        if (expression instanceof PsiReferenceExpression) {
          if (myThrowableClass.isValid()) {
            typeReference.replace(factory.createClassReferenceElement(myThrowableClass));
          }
        }
        else {
          typeReference.replace(expression);
        }

        throwableValue = newExpression;
      }

      PsiThrowStatement throwStatement =
        (PsiThrowStatement)factory.createStatementFromText("throw expr;", context);

      PsiExpression exception = throwStatement.getException();
      assert exception != null;
      exception.replace(throwableValue);

      return throwStatement;
    }

    @Override
    protected void postProcess(@NotNull final InsertionContext context, @NotNull PsiThrowStatement statement) {
      if (myThrowableClass != null) {
        PsiNewExpression newExpression = (PsiNewExpression)statement.getException();
        assert newExpression != null;

        CaretModel caretModel = context.getEditor().getCaretModel();
        PsiExpressionList argumentList = newExpression.getArgumentList();
        assert argumentList != null;

        if (myAccessibility == CtorAccessibility.WithParametricCtor ||
            myAccessibility == CtorAccessibility.NotAccessible) { // new Throwable(<caret>)
          caretModel.moveToOffset(argumentList.getFirstChild().getTextRange().getEndOffset());
          return;
        }

        if (myTypeRequiresRefinement) {
          PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
          assert anonymousClass != null;

          PsiElement lBrace = anonymousClass.getLBrace();
          assert lBrace != null;

          caretModel.moveToOffset(lBrace.getTextRange().getEndOffset());
        }
      }

      super.postProcess(context, statement); // throw new Throwable();<caret>
    }
  }
}