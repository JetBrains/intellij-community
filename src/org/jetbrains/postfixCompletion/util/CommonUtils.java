package org.jetbrains.postfixCompletion.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.templates.PostfixTemplate;

public abstract class CommonUtils {
  private CommonUtils() {
  }

  @NotNull
  public static CtorAccessibility isTypeCanBeInstantiatedWithNew(
    @Nullable PsiClass psiClass, @NotNull PsiElement accessContext) {
    if (psiClass == null) return CtorAccessibility.NotAccessible;

    if (psiClass.isEnum()) return CtorAccessibility.NotAccessible;
    if (psiClass.isInterface()) return CtorAccessibility.WithDefaultCtor;

    PsiClass containingType = PsiTreeUtil.getParentOfType(accessContext, PsiClass.class);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(accessContext.getProject());
    PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();

    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) return CtorAccessibility.WithDefaultCtor;

    boolean hasAccessibleCtors = false, hasParametricCtors = false;

    for (PsiMethod constructor : constructors) {
      if (resolveHelper.isAccessible(constructor, accessContext, containingType)) {
        hasAccessibleCtors = true;
        int parametersCount = constructor.getParameterList().getParametersCount();
        if (parametersCount != 0) hasParametricCtors = true;
      }
    }

    if (!hasAccessibleCtors) return CtorAccessibility.NotAccessible;

    return hasParametricCtors ? CtorAccessibility.WithParametricCtor
      : CtorAccessibility.WithDefaultCtor;
  }

  public static void showErrorHint(Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, "Can't perform postfix completion", "Can't perform postfix completion", "");
  }

  public static void createSimpleStatement(@NotNull PsiElement context, @NotNull Editor editor, @NotNull String text) {
    PsiExpression expr = PostfixTemplate.getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    assert parent instanceof PsiStatement;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    PsiStatement assertStatement = factory.createStatementFromText(text + " " + expr.getText() + ";", parent);
    PsiElement replace = parent.replace(assertStatement);
    editor.getCaretModel().moveToOffset(replace.getTextRange().getEndOffset());
  }

  public enum CtorAccessibility {
    NotAccessible,
    WithDefaultCtor,
    WithParametricCtor
  }

  public static boolean isTypeRequiresRefinement(@Nullable PsiClass psiClass) {
    if (psiClass == null) return false;
    return psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
  }
}

