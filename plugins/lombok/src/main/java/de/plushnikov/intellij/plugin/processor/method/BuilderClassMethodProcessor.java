package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Inspect and validate @Builder lombok annotation on a method
 * Creates inner class for a builder pattern
 *
 * @author Tomasz Kalkosiński
 * @author Michail Plushnikov
 */
public class BuilderClassMethodProcessor extends AbstractMethodProcessor {

  private static final String BUILDER_SHORT_NAME = StringUtil.getShortName(LombokClassNames.BUILDER);

  public BuilderClassMethodProcessor() {
    super(PsiClass.class, LombokClassNames.BUILDER);
  }

  @Override
  public boolean notNameHintIsEqualToSupportedAnnotation(@Nullable String nameHint) {
    return !"lombok".equals(nameHint) && !BUILDER_SHORT_NAME.equals(nameHint);
  }

  @Override
  protected boolean possibleToGenerateElementNamed(@Nullable String nameHint, @NotNull PsiClass psiClass,
                                                   @NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod) {
    if (null == nameHint) {
      return true;
    }

    final String innerBuilderClassName = getHandler().getBuilderClassName(psiClass, psiAnnotation, psiMethod);
    return Objects.equals(nameHint, innerBuilderClassName);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    return getHandler().validate(psiMethod, psiAnnotation, builder);
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiClass psiClass = psiMethod.getContainingClass();
    if (null != psiClass) {
      final BuilderHandler builderHandler = getHandler();
      builderHandler.createBuilderClassIfNotExist(psiClass, psiMethod, psiAnnotation).ifPresent(target::add);
    }
  }

  private BuilderHandler getHandler() {
    return ApplicationManager.getApplication().getService(BuilderHandler.class);
  }
}
