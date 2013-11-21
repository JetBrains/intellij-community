package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.editor.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

import static org.jetbrains.postfixCompletion.CommonUtils.*;

@TemplateProvider(
  templateName = "new",
  description = "Produces instantiation expression for type",
  example = "new SomeType()",
  worksOnTypes = true)
public final class NewExpressionTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression();

    PsiElement referencedElement = context.innerExpression().referencedElement;
    if (referencedElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass) referencedElement;
      CtorAccessibility accessibility =
        CommonUtils.isTypeCanBeInstantiatedWithNew(psiClass, expression.expression);
      if (accessibility == CtorAccessibility.NotAccessible)
        return;

      consumer.add(new NewObjectLookupElement(expression, psiClass, accessibility));
    }
  }

  private static class NewObjectLookupElement extends ExpressionPostfixLookupElement<PsiNewExpression> {
    @NotNull private final CtorAccessibility myAccessibility;
    private final boolean myTypeRequiresRefinement;

    public NewObjectLookupElement(
        @NotNull PrefixExpressionContext context, @NotNull PsiClass referencedElement,
        @NotNull CtorAccessibility accessibility) {
      super("new", context);
      myAccessibility = accessibility;
      myTypeRequiresRefinement = CommonUtils.isTypeRequiresRefinement(referencedElement);
    }

    @NotNull @Override protected PsiNewExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      String template = "new T()";
      if (myTypeRequiresRefinement) template += "{}";

      PsiNewExpression newExpression = (PsiNewExpression) factory.createExpressionFromText(template, context);
      PsiJavaCodeReferenceElement typeReference = newExpression.getClassOrAnonymousClassReference();
      assert (typeReference != null) : "typeReference != null";

      typeReference.replace(expression);

      return newExpression;
    }

    @Override protected void postProcess(
      @NotNull final InsertionContext context, @NotNull PsiNewExpression expression) {

      CaretModel caretModel = context.getEditor().getCaretModel();
      PsiExpressionList argumentList = expression.getArgumentList();
      assert argumentList != null : "argumentList != null";

      if (myAccessibility == CtorAccessibility.WithParametricCtor) { // new T(<caret>)
        caretModel.moveToOffset(argumentList.getFirstChild().getTextRange().getEndOffset());
      } else if (myTypeRequiresRefinement) {
        PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
        assert (anonymousClass != null) : "anonymousClass != null";

        PsiElement lBrace = anonymousClass.getLBrace();
        assert (lBrace != null) : "lBrace != null";

        caretModel.moveToOffset(lBrace.getTextRange().getEndOffset());
      } else { // new T()<caret>
        caretModel.moveToOffset(argumentList.getTextRange().getEndOffset());
      }
    }
  }
}