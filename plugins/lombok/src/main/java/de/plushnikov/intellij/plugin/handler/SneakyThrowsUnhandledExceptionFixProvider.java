package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.UnhandledExceptionFixProvider;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.quickfix.PsiQuickFixFactory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Adds @SneakyThrows Annotation as Fix to handle unhandled exceptions
 */
public class SneakyThrowsUnhandledExceptionFixProvider implements UnhandledExceptionFixProvider {
  @Override
  public void registerUnhandledExceptionFixes(@NotNull HighlightInfo.Builder info,
                                              @NotNull PsiElement element,
                                              @NotNull List<PsiClassType> unhandledExceptions) {
    PsiElement importantParent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLambdaExpression.class,
                                                             PsiMethodReferenceExpression.class, PsiClassInitializer.class);

    // applicable only for methods
    if (importantParent instanceof PsiMethod psiMethod) {
      final PsiMethodCallExpression thisOrSuperCallInConstructor = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(psiMethod);
      if (null == thisOrSuperCallInConstructor ||
          !SneakyThrowsExceptionHandler.throwsExceptionsTypes(thisOrSuperCallInConstructor, unhandledExceptions)) {

        AddAnnotationFix fix = PsiQuickFixFactory.createAddAnnotationFix(LombokClassNames.SNEAKY_THROWS, psiMethod);
        info.registerFix(fix, null, null, null, null);
      }
    }
  }
}
