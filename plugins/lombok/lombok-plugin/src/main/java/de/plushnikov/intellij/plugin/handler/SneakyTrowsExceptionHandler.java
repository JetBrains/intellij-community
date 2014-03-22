package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class SneakyTrowsExceptionHandler {

  private static final String ANNOTATION_FQN = SneakyThrows.class.getName();

  public static boolean isExceptionHandled(@NotNull PsiModifierListOwner psiModifierListOwner, @NotNull String exceptionFQN) {
    final PsiAnnotation psiAnnotation = AnnotationUtil.findAnnotation(psiModifierListOwner, ANNOTATION_FQN);
    if (psiAnnotation == null) {
      return false;
    }

    final Collection<PsiType> sneakedExceptionTypes = PsiAnnotationUtil.getAnnotationValues(psiAnnotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, PsiType.class);
    //Default SneakyThrows handles all exceptions
    if (sneakedExceptionTypes.isEmpty()) {
      return true;
    }

    for (PsiType sneakedExceptionType : sneakedExceptionTypes) {
      if (sneakedExceptionType.equalsToText(exceptionFQN)) {
        return true;
      }
    }

    final Project project = psiModifierListOwner.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiClassType unhandledExceptionType = factory.createTypeByFQClassName(exceptionFQN, GlobalSearchScope.allScope(project));
    final PsiClass unhandledExceptionClass = unhandledExceptionType.resolve();

    if (null != unhandledExceptionClass) {
      for (PsiType sneakedExceptionType : sneakedExceptionTypes) {
        if (sneakedExceptionType instanceof PsiClassType) {
          final PsiClass sneakedExceptionClass = ((PsiClassType) sneakedExceptionType).resolve();

          if (null != sneakedExceptionClass && sneakedExceptionClass.isInheritor(unhandledExceptionClass, true)) {
            return true;
          }
        }
      }
    }

    return false;
  }
}
