package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class ToStringProcessor extends AbstractLombokClassProcessor {

  private static final String CLASS_NAME = ToString.class.getName();

  public ToStringProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Project project = psiClass.getProject();
    PsiManager manager = psiClass.getContainingFile().getManager();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    PsiMethod toStringMethod = createToStringMethod(psiClass, elementFactory);
    target.add((Psi) prepareMethod(manager, toStringMethod, psiClass, psiAnnotation));
    //TODO add read usage for fields UserMapKeys.addReadUsageFor(psiField);
  }

  @NotNull
  private PsiMethod createToStringMethod(@NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    return elementFactory.createMethodFromText(
        "public java.lang.String toString() { return super.toString();}",
        psiClass);
  }

}
