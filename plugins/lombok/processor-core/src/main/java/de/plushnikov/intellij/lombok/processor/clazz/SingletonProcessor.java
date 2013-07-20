package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import de.plushnikov.intellij.lombok.ErrorMessages;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Singleton;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspect and validate @Singleton lombok-pg annotation on a class
 * Creates getInstance() method for this singleton class
 *
 * @author Plushnikov Michail
 */
public class SingletonProcessor extends AbstractLombokClassProcessor {

  public static final String METHOD_NAME = "getInstance";

  public SingletonProcessor() {
    super(Singleton.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = validateAnnotationOnRigthType(psiClass, builder);
    if (result) {
      result = validateExistingMethods(psiClass, builder);
    }

    if (PsiClassUtil.hasSuperClass(psiClass)) {
      builder.addError(ErrorMessages.canBeUsedOnConcreteClassOnly(Singleton.class));
      result = false;
    }
    if (PsiClassUtil.hasMultiArgumentConstructor(psiClass)) {
      builder.addError(ErrorMessages.requiresDefaultOrNoArgumentConstructor(Singleton.class));
      result = false;
    }

    return result;
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface() || psiClass.isEnum()) {
      builder.addError(ErrorMessages.canBeUsedOnClassOnly(Singleton.class));
      result = false;
    }
    return result;
  }

  protected boolean validateExistingMethods(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (PsiMethodUtil.hasMethodByName(classMethods, METHOD_NAME)) {
      builder.addWarning(String.format("Not generated '%s'(): A method with same name already exists", METHOD_NAME));
      result = false;
    }

    return result;
  }

  protected void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    LombokLightMethodBuilder method = LombokPsiElementFactory.getInstance().createLightMethod(psiClass.getManager(), METHOD_NAME)
        .withMethodReturnType(PsiClassUtil.getClassType(psiClass))
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(PsiModifier.STATIC)
        .withModifier(PsiModifier.PUBLIC);

    target.add(method);
  }
}
