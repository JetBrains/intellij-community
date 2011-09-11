package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.psi.MyLightMethod;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class AllArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  private static final String CLASS_NAME = AllArgsConstructor.class.getName();

  public AllArgsConstructorProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> boolean process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Project project = psiClass.getProject();
    PsiManager manager = psiClass.getContainingFile().getManager();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    final String visibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    if (null != visibility) {

      Collection<PsiField> allNotInitializedNotStaticFields = getAllNotInitializedAndNotStaticFields(psiClass);

      PsiMethod constructorMethod = createConstructorMethod(visibility, allNotInitializedNotStaticFields, psiClass, elementFactory);
      target.add((Psi) new MyLightMethod(manager, constructorMethod, psiClass));
    }
    return true;
  }


}
