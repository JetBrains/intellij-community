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
public class NewExpressionTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
      @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression;

    PsiElement referencedElement = context.innerExpression.referencedElement;
    if (referencedElement instanceof PsiClass) {

      CtorAccessibility accessibility = CommonUtils
        .isTypeCanBeInstantiatedWithNew((PsiClass) referencedElement, expression.expression);
      if (accessibility == CtorAccessibility.NotAccessible)
        return;

      // todo: is instantiable type check

      consumer.add(new NewObjectLookupElement(expression, (PsiClass) referencedElement, accessibility));
    }
  }

  private static class NewObjectLookupElement extends ExpressionPostfixLookupElement<PsiNewExpression> {
    @NotNull private final PsiClass myReferencedElement;
    @NotNull private final CtorAccessibility myAccessibility;
    private final boolean myTypeRequiresRefinement;

    public NewObjectLookupElement(
        @NotNull PrefixExpressionContext context,
        @NotNull PsiClass referencedElement,
        @NotNull CtorAccessibility accessibility) {
      super("new", context);
      myReferencedElement = referencedElement;
      myAccessibility = accessibility;
      myTypeRequiresRefinement = CommonUtils.isTypeRequiresRefinement(referencedElement);
    }

    @NotNull @Override protected PsiNewExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiElement context) {

      String template = "new T()";
      if (myTypeRequiresRefinement) {
        template += "{}";
      }

      PsiNewExpression newExpression = (PsiNewExpression) factory.createExpressionFromText(template, context);
      PsiJavaCodeReferenceElement referenceElement = factory.createClassReferenceElement(myReferencedElement);

      PsiJavaCodeReferenceElement ref = newExpression.getClassOrAnonymousClassReference();
      assert ref != null;

      ref.replace(referenceElement);

      // todo: fix type?

      return newExpression;
    }

    // TODO: test and cleanup
    @Override protected void postProcess(
      @NotNull final InsertionContext context, @NotNull PsiNewExpression expression) {

      CaretModel caretModel = context.getEditor().getCaretModel();
      PsiExpressionList argumentList = expression.getArgumentList();
      assert argumentList != null : "argumentList != null";

      if (myAccessibility == CtorAccessibility.WithParametricCtor) { // new T(<caret>)
        caretModel.moveToOffset(argumentList.getFirstChild().getTextRange().getEndOffset());
      } else if (myTypeRequiresRefinement) {
        caretModel.moveToOffset(
          expression.getAnonymousClass().getRBrace().getTextRange().getEndOffset());
      } else { // new T()<caret>
        caretModel.moveToOffset(argumentList.getTextRange().getEndOffset());
      }
    }
  }
}