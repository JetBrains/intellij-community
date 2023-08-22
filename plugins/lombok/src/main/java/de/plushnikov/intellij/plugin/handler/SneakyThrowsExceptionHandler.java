package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.CustomExceptionHandler;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class SneakyThrowsExceptionHandler extends CustomExceptionHandler {

  private static final String JAVA_LANG_THROWABLE = "java.lang.Throwable";

  @Override
  public boolean isHandled(@Nullable PsiElement element, @NotNull PsiClassType exceptionType, PsiElement topElement) {
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class, PsiTryStatement.class, PsiMethod.class);
    if (parent instanceof PsiLambdaExpression) {
      // lambda it's another scope, @SneakyThrows annotation can't neglect exceptions in lambda only on method, constructor
      return false;
    } else if (parent instanceof PsiTryStatement && isHandledByTryCatch(exceptionType, (PsiTryStatement) parent)) {
      // that exception MAY be already handled by regular try-catch statement
      return false;
    }

    if (topElement instanceof PsiTryStatement && isHandledByTryCatch(exceptionType, (PsiTryStatement) topElement)) {
      // that exception MAY be already handled by regular try-catch statement (don't forget about nested try-catch)
      return false;
    } else if (!(topElement instanceof PsiCodeBlock)) {
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      return psiMethod != null && isExceptionHandled(psiMethod, exceptionType);
    }
    return false;
  }

  private static boolean isHandledByTryCatch(@NotNull PsiClassType exceptionType, PsiTryStatement topElement) {
    List<PsiType> caughtExceptions = ContainerUtil.map(topElement.getCatchBlockParameters(), PsiParameter::getType);
    return isExceptionHandled(exceptionType, caughtExceptions);
  }

  private static boolean isExceptionHandled(@NotNull PsiModifierListOwner psiModifierListOwner, PsiClassType exceptionClassType) {
    final PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiModifierListOwner, LombokClassNames.SNEAKY_THROWS);
    if (psiAnnotation == null) {
      return false;
    }

    final Collection<PsiType> sneakedExceptionTypes = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, PsiType.class);
    //Default SneakyThrows handles all exceptions
    return sneakedExceptionTypes.isEmpty()
      || sneakedExceptionTypes.iterator().next().equalsToText(JAVA_LANG_THROWABLE)
      || isExceptionHandled(exceptionClassType, sneakedExceptionTypes);
  }

  private static boolean isExceptionHandled(@NotNull PsiClassType exceptionClassType, @NotNull Collection<PsiType> sneakedExceptionTypes) {
    for (PsiType sneakedExceptionType : sneakedExceptionTypes) {
      if (sneakedExceptionType.equalsToText(JAVA_LANG_THROWABLE) || sneakedExceptionType.equals(exceptionClassType)) {
        return true;
      }
    }

    final PsiClass unhandledExceptionClass = exceptionClassType.resolve();

    if (null != unhandledExceptionClass) {
      for (PsiType sneakedExceptionType : sneakedExceptionTypes) {
        if (sneakedExceptionType instanceof PsiClassType) {
          final PsiClass sneakedExceptionClass = ((PsiClassType) sneakedExceptionType).resolve();

          if (null != sneakedExceptionClass && unhandledExceptionClass.isInheritor(sneakedExceptionClass, true)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
