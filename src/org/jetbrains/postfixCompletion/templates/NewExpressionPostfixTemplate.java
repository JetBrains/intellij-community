package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.ExpressionPostfixLookupElementBase;
import org.jetbrains.postfixCompletion.util.CommonUtils;

@TemplateInfo(
  templateName = "new",
  description = "Produces instantiation expression for type",
  example = "new SomeType()",
  worksOnTypes = true)
public class NewExpressionPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext expression = context.outerExpression();

    PsiElement referencedElement = context.innerExpression().referencedElement;
    if (referencedElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)referencedElement;
      CtorAccessibility accessibility =
        isTypeCanBeInstantiatedWithNew(psiClass, expression.expression);
      if (accessibility == CtorAccessibility.NOT_ACCESSIBLE &&
          !context.executionContext.isForceMode) {
        return null;
      }

      return new NewObjectLookupElement(expression, psiClass, accessibility);
    }

    return null;
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    throw new UnsupportedOperationException("Implement me please");
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
    protected PsiNewExpression createNewExpression(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {
      String template = "new T()";
      if (myTypeRequiresRefinement) template += "{}";

      PsiNewExpression newExpression = (PsiNewExpression)factory.createExpressionFromText(template, context);
      PsiJavaCodeReferenceElement typeReference = newExpression.getClassOrAnonymousClassReference();
      assert typeReference != null;

      typeReference.replace(expression);

      return newExpression;
    }

    @Override
    protected void postProcess(@NotNull final InsertionContext context, @NotNull PsiNewExpression expression) {
      CaretModel caretModel = context.getEditor().getCaretModel();
      PsiExpressionList argumentList = expression.getArgumentList();
      assert argumentList != null;

      if (myAccessibility == CtorAccessibility.WITH_PARAMETRIC_CTOR ||
          myAccessibility == CtorAccessibility.NOT_ACCESSIBLE) { // new T(<caret>)
        caretModel.moveToOffset(argumentList.getFirstChild().getTextRange().getEndOffset());
      }
      else if (myTypeRequiresRefinement) {
        PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
        assert anonymousClass != null;

        PsiElement lBrace = anonymousClass.getLBrace();
        assert lBrace != null;

        caretModel.moveToOffset(lBrace.getTextRange().getEndOffset());
      }
      else { // new T()<caret>
        caretModel.moveToOffset(argumentList.getTextRange().getEndOffset());
      }
    }
  }

  public enum CtorAccessibility {
    NOT_ACCESSIBLE,
    WITH_DEFAULT_CTOR,
    WITH_PARAMETRIC_CTOR
  }

  private static CtorAccessibility isTypeCanBeInstantiatedWithNew(
    @Nullable PsiClass psiClass, @NotNull PsiElement accessContext) {
    if (psiClass == null) return CtorAccessibility.NOT_ACCESSIBLE;

    if (psiClass.isEnum()) return CtorAccessibility.NOT_ACCESSIBLE;
    if (psiClass.isInterface()) return CtorAccessibility.WITH_DEFAULT_CTOR;

    PsiClass containingType = PsiTreeUtil.getParentOfType(accessContext, PsiClass.class);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(accessContext.getProject());
    PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();

    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) return CtorAccessibility.WITH_DEFAULT_CTOR;

    boolean hasAccessibleCtors = false, hasParametricCtors = false;

    for (PsiMethod constructor : constructors) {
      if (resolveHelper.isAccessible(constructor, accessContext, containingType)) {
        hasAccessibleCtors = true;
        int parametersCount = constructor.getParameterList().getParametersCount();
        if (parametersCount != 0) hasParametricCtors = true;
      }
    }

    if (!hasAccessibleCtors) return CtorAccessibility.NOT_ACCESSIBLE;

    return hasParametricCtors ? CtorAccessibility.WITH_PARAMETRIC_CTOR : CtorAccessibility.WITH_DEFAULT_CTOR;
  }
}