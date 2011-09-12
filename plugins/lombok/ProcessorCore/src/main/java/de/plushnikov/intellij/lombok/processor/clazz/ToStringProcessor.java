package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.lombok.psi.MyLightMethod;
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

  public <Psi extends PsiElement> boolean process(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    Project project = psiClass.getProject();
    PsiManager manager = psiClass.getContainingFile().getManager();
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    PsiMethod toStringMethod = createToStringMethod(psiClass, elementFactory);
    target.add((Psi) new MyLightMethod(manager, toStringMethod, psiClass));
    return true;
  }

  private PsiMethod createToStringMethod(PsiClass psiClass, PsiElementFactory elementFactory) {
    return elementFactory.createMethodFromText(
        "public java.lang.String toString() { return super.toString();}",
        psiClass);
  }

}
