package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class UtilityClassProcessor extends AbstractClassProcessor {

  private static final String LOMBOK_UTILITY_CLASS_FQN = "lombok.experimental.UtilityClass";
  private static final String LOMBOK_UTILITY_CLASS_SHORT_NAME = "UtilityClass";

  public UtilityClassProcessor() {
    super(PsiMethod.class, UtilityClass.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateOnRightType(psiClass, builder) && validateNoConstructorsDefined(psiClass, builder);
  }

  private boolean validateNoConstructorsDefined(PsiClass psiClass, ProblemBuilder builder) {
    Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassConstructorIntern(psiClass);
    if(!psiMethods.isEmpty()) {
      builder.addError("@UtilityClasses cannot have declared constructors.");
      return false;
    }
    return true;
  }

  protected boolean validateOnRightType(PsiClass psiClass, ProblemBuilder builder) {
    if (checkType(psiClass)) {
      builder.addError("@UtilityClass is only supported on a class (can't be an interface, enum, or annotation).");
      return false;
    }
    PsiElement context = psiClass.getContext();
    if(context == null) return false;
    if(!(context instanceof PsiFile)) {
      PsiElement contextUp = context;
      while(true) {
        if(contextUp instanceof PsiClass) {
          PsiClass psiClassUp = (PsiClass) contextUp;
          if(psiClassUp.getContext() instanceof PsiFile) return true;
          Boolean isStatic = isStatic(psiClassUp.getModifierList());
          if(isStatic || checkType(psiClassUp)) {
            contextUp = contextUp.getContext();
          }
          else {
            builder.addError("@UtilityClass automatically makes the class static, however, this class cannot be made static.");
            return false;
          }
        }
        else {
          builder.addError("@UtilityClass cannot be placed on a method local or anonymous inner class, or any class nested in such a class.");
          return false;
        }
      }
    }
    return true;
  }

  private boolean isStatic(PsiModifierList modifierList) {
    return modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC);
  }

  private boolean checkType(PsiClass psiClass) {
    return psiClass.isInterface() || psiClass.isEnum() || psiClass.isAnnotationType();
  }

  @Override
  protected void generatePsiElements(@NotNull final PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {

    LombokLightMethodBuilder constructor = new LombokLightMethodBuilder(psiClass.getManager(), psiClass.getName())
        .withConstructor(true)
        .withContainingClass(psiClass)
        .withNavigationElement(psiAnnotation)
        .withModifier(PsiModifier.PRIVATE);

    String methodBody = String.format("throw new %s(%s)", "java.lang.UnsupportedOperationException", "This is a utility class and cannot be instantiated");

    constructor.withBody(PsiMethodUtil.createCodeBlockFromText(methodBody, psiClass));
    target.add(constructor);
  }

  public Boolean hasModifierProperty(@NotNull PsiModifierList psiModifierList, @NotNull String name) {

    PsiElement parent = psiModifierList.getParent();

    if(PsiModifier.STATIC.equals(name) && isElementFieldMethodOrInnerClass(parent) && parentClassOfElementIsUtilityClass(parent)) {
      return Boolean.TRUE;
    }

    return null;
  }

  private boolean isElementFieldMethodOrInnerClass(PsiElement element) {
    return element instanceof PsiField || element instanceof PsiMethod || (element instanceof PsiClass && element.getParent() instanceof PsiClass);
  }

  private boolean parentClassOfElementIsUtilityClass(PsiElement element) {
    if(!(element.getParent() instanceof PsiClass)) return false;

    PsiClass parent = (PsiClass) element.getParent();

    PsiModifierList modifierList = parent.getModifierList();

    return (modifierList != null && (modifierList.findAnnotation(LOMBOK_UTILITY_CLASS_FQN) != null || modifierList.findAnnotation(LOMBOK_UTILITY_CLASS_SHORT_NAME) != null));
  }
}