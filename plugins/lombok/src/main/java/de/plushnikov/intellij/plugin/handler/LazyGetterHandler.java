package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.InitializationUtils;
import org.jetbrains.annotations.NotNull;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.Getter;

public class LazyGetterHandler {

  public static boolean isLazyGetterHandled(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (!(element instanceof PsiIdentifier)) {
      return false;
    }
    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field == null) {
      return false;
    }

    final PsiAnnotation getterAnnotation = PsiAnnotationSearchUtil.findAnnotation(field, Getter.class);
    return null != getterAnnotation && PsiAnnotationUtil.getBooleanAnnotationValue(getterAnnotation, "lazy", false);
  }

  public static boolean isInitializedInConstructors(@NotNull HighlightInfo highlightInfo, @NotNull PsiFile file) {
    PsiElement element = file.findElementAt(highlightInfo.getStartOffset());
    if (!(element instanceof PsiIdentifier)) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiElement qualifier = ((PsiReferenceExpression) parent).getQualifier();
    if (qualifier == null) {
      return false;
    }
    PsiReference reference = qualifier.getReference();
    if (reference == null) {
      return false;
    }
    PsiElement field = reference.resolve();
    if (!(field instanceof PsiField)) {
      return false;
    }
    PsiClass containingClass = ((PsiField) field).getContainingClass();
    if (containingClass == null) {
      return false;
    }
    return isInitializedInConstructors((PsiField) field, containingClass);
  }

  public static boolean isInitializedInConstructors(@NotNull PsiField field, @NotNull PsiClass aClass) {
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      return false;
    }

    for (final PsiMethod constructor : constructors) {
      if (!InitializationUtils.methodAssignsVariableOrFails(constructor, field)) {
        return false;
      }
    }
    return true;
  }
}
