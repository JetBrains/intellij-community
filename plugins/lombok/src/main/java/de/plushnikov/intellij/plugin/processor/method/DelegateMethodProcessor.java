package de.plushnikov.intellij.plugin.processor.method;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.processor.handler.DelegateHandler;
import de.plushnikov.intellij.plugin.settings.ProjectSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DelegateMethodProcessor extends AbstractMethodProcessor {

  @SuppressWarnings({"deprecation"})
  public DelegateMethodProcessor() {
    super(PsiMethod.class, lombok.Delegate.class, lombok.experimental.Delegate.class);
  }

  private DelegateHandler getDelegateHandler() {
    return ServiceManager.getService(DelegateHandler.class);
  }

  @Override
  public boolean isEnabled(@NotNull Project project) {
    return ProjectSettings.isEnabled(project, ProjectSettings.IS_DELEGATE_ENABLED);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiMethod.getParameterList().getParametersCount() > 0) {
      builder.addError("@Delegate is legal only on no-argument methods.");
      result = false;
    }

    final PsiType returnType = psiMethod.getReturnType();
    final DelegateHandler handler = getDelegateHandler();
    result &= null != returnType && handler.validate(psiMethod, returnType, psiAnnotation, builder);

    return result;
  }

  @Override
  protected void processIntern(@NotNull PsiMethod psiMethod, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final PsiType returnType = psiMethod.getReturnType();
    if (null != returnType) {
      final DelegateHandler handler = getDelegateHandler();
      handler.generateElements(psiMethod, returnType, psiAnnotation, target);
    }
  }
}
