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
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.lombok.UserMapKeys;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class ToStringProcessor extends AbstractLombokClassProcessor {

  private static final String CLASS_NAME = ToString.class.getName();
  public static final String METHOD_NAME = "toString";

  public ToStringProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  public <Psi extends PsiElement> void process(@NotNull PsiClass psiClass, @NotNull PsiMethod[] classMethods, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    if (!hasMethodByName(classMethods, METHOD_NAME)) {
      Project project = psiClass.getProject();
      PsiManager manager = psiClass.getContainingFile().getManager();
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

      PsiMethod toStringMethod = prepareMethod(manager, createToStringMethod(psiClass, elementFactory), psiClass, psiAnnotation);
      target.add((Psi) toStringMethod);

      Collection<PsiField> toStringFields = filterFieldsByModifiers(psiClass.getFields(), PsiModifier.STATIC);
      UserMapKeys.addReadUsageFor(toStringFields);
    } else {
      //TODO create warning in code
      //Not generating methodName(): A method with that name already exists
    }
  }

  @NotNull
  private PsiMethod createToStringMethod(@NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    return elementFactory.createMethodFromText(
        "public java.lang.String " + METHOD_NAME + "() { return super.toString(); }",
        psiClass);
  }

}
