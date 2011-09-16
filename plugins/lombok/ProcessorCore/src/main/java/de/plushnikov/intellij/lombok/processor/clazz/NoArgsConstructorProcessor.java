package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class NoArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  private static final String CLASS_NAME = NoArgsConstructor.class.getName();

  public NoArgsConstructorProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Project project = psiClass.getProject();
    PsiManager manager = psiClass.getContainingFile().getManager();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    final String visibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    if (null != visibility) {
      PsiMethod constructorMethod = createConstructorMethod(visibility, Collections.<PsiField>emptyList(), psiClass, elementFactory);
      target.add((Psi) prepareMethod(manager, constructorMethod, psiClass, psiAnnotation));
    }
  }

}
