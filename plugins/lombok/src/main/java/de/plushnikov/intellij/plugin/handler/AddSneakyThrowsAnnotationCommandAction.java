package de.plushnikov.intellij.plugin.handler;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds @SneakyThrows Annotation as Fix to handle unhandled exceptions
 */
public class AddSneakyThrowsAnnotationCommandAction extends PsiUpdateModCommandAction<PsiElement> {

  public AddSneakyThrowsAnnotationCommandAction(@NotNull PsiElement context) {
    super(context);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.add.annotation.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiElement importantParent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiLambdaExpression.class,
                                                             PsiMethodReferenceExpression.class, PsiClassInitializer.class);

    // applicable only for methods
    if (importantParent instanceof PsiMethod psiMethod) {
      final PsiMethodCallExpression thisOrSuperCallInConstructor = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(psiMethod);
      if (null == thisOrSuperCallInConstructor ||
          !SneakyThrowsExceptionHandler.throwsExceptionsTypes(thisOrSuperCallInConstructor,
                                                              ExceptionUtil.getOwnUnhandledExceptions(element))) {

        return Presentation.of(AddAnnotationPsiFix.calcText(psiMethod, LombokClassNames.SNEAKY_THROWS));
      }
    }
    return null;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (null != psiMethod) {
      final PsiAnnotation addedAnnotation = psiMethod.getModifierList().addAnnotation(LombokClassNames.SNEAKY_THROWS);
      JavaCodeStyleManager.getInstance(element.getProject()).shortenClassReferences(addedAnnotation);
    }
  }
}
