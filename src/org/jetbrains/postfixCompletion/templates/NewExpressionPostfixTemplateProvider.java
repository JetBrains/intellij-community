package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElementBase;
import org.jetbrains.postfixCompletion.util.CommonUtils;

import java.util.List;

import static org.jetbrains.postfixCompletion.util.CommonUtils.CtorAccessibility;

// todo: force mode!

@TemplateProvider(
  templateName = "new",
  description = "Produces instantiation expression for type",
  example = "new SomeType()",
  worksOnTypes = true)
public final class NewExpressionPostfixTemplateProvider extends PostfixTemplateProvider {
  @Override
  public void createItems(@NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression();

    PsiElement referencedElement = context.innerExpression().referencedElement;
    if (referencedElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)referencedElement;
      CtorAccessibility accessibility =
        CommonUtils.isTypeCanBeInstantiatedWithNew(psiClass, expression.expression);
      if (accessibility == CtorAccessibility.NotAccessible &&
          !context.executionContext.isForceMode) {
        return;
      }

      consumer.add(new NewObjectLookupElement(expression, psiClass, accessibility));
    }
  }

  static class NewObjectLookupElement extends ExpressionPostfixLookupElementBase<PsiNewExpression> {
    @NotNull private final CtorAccessibility myAccessibility;
    private final boolean myTypeRequiresRefinement;

    public NewObjectLookupElement(@NotNull PrefixExpressionContext context, @NotNull PsiClass referencedElement,
      @NotNull CtorAccessibility accessibility) {
      super("new", context);
      myAccessibility = accessibility;
      myTypeRequiresRefinement = CommonUtils.isTypeRequiresRefinement(referencedElement);
    }

    @NotNull
    @Override
    protected PsiNewExpression createNewExpression(@NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      String template = "new T()";
      if (myTypeRequiresRefinement) template += "{}";

      PsiNewExpression newExpression = (PsiNewExpression)factory.createExpressionFromText(template, context);
      PsiJavaCodeReferenceElement typeReference = newExpression.getClassOrAnonymousClassReference();
      assert (typeReference != null) : "typeReference != null";

      typeReference.replace(expression);

      return newExpression;
    }

    @Override
    protected void postProcess(@NotNull final InsertionContext context, @NotNull PsiNewExpression expression) {
      CaretModel caretModel = context.getEditor().getCaretModel();
      PsiExpressionList argumentList = expression.getArgumentList();
      assert (argumentList != null) : "argumentList != null";

      if (myAccessibility == CtorAccessibility.WithParametricCtor ||
          myAccessibility == CtorAccessibility.NotAccessible) { // new T(<caret>)
        caretModel.moveToOffset(argumentList.getFirstChild().getTextRange().getEndOffset());
      }
      else if (myTypeRequiresRefinement) {
        PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
        assert (anonymousClass != null) : "anonymousClass != null";

        PsiElement lBrace = anonymousClass.getLBrace();
        assert (lBrace != null) : "lBrace != null";

        caretModel.moveToOffset(lBrace.getTextRange().getEndOffset());
      }
      else { // new T()<caret>
        caretModel.moveToOffset(argumentList.getTextRange().getEndOffset());
      }
    }
  }
}