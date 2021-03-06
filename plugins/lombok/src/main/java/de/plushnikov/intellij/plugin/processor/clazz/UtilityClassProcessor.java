package de.plushnikov.intellij.plugin.processor.clazz;

import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import de.plushnikov.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Florian Böhm
 */
public class UtilityClassProcessor extends AbstractClassProcessor {

  public UtilityClassProcessor() {
    super(PsiMethod.class, LombokClassNames.UTILITY_CLASS);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    return validateOnRightType(psiClass, builder) && validateNoConstructorsDefined(psiClass, builder);
  }

  private boolean validateNoConstructorsDefined(PsiClass psiClass, ProblemBuilder builder) {
    Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassConstructorIntern(psiClass);
    if (!psiMethods.isEmpty()) {
      builder.addError(LombokBundle.message("inspection.message.utility.classes.cannot.have.declared.constructors"));
      return false;
    }
    return true;
  }

  public static boolean validateOnRightType(PsiClass psiClass, ProblemBuilder builder) {
    if (checkWrongType(psiClass)) {
      builder.addError(LombokBundle.message("inspection.message.utility.class.only.supported.on.class"));
      return false;
    }
    PsiElement context = psiClass.getContext();
    if (context == null) {
      return false;
    }
    if (!(context instanceof PsiFile)) {
      PsiElement contextUp = context;
      while (true) {
        if (contextUp instanceof PsiClass) {
          PsiClass psiClassUp = (PsiClass) contextUp;
          if (psiClassUp.getContext() instanceof PsiFile) {
            return true;
          }
          boolean isStatic = isStatic(psiClassUp.getModifierList());
          if (isStatic || checkWrongType(psiClassUp)) {
            contextUp = contextUp.getContext();
          } else {
            builder.addError(LombokBundle.message("inspection.message.utility.class.automatically.makes.class.static"));
            return false;
          }
        } else {
          builder.addError(LombokBundle.message("inspection.message.utility.class.cannot.be.placed"));
          return false;
        }
      }
    }
    return true;
  }

  private static boolean isStatic(PsiModifierList modifierList) {
    return modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean checkWrongType(PsiClass psiClass) {
    return psiClass != null && (psiClass.isInterface() || psiClass.isEnum() || psiClass.isAnnotationType());
  }

  @Override
  protected void generatePsiElements(@NotNull final PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {

    LombokLightMethodBuilder constructorBuilder = new LombokLightMethodBuilder(psiClass.getManager(), psiClass.getName())
      .withConstructor(true)
      .withContainingClass(psiClass)
      .withNavigationElement(psiAnnotation)
      .withModifier(PsiModifier.PRIVATE);

    String methodBody = String.format("throw new %s(%s);", "java.lang.UnsupportedOperationException", "\"This is a utility class and cannot be instantiated\"");

    constructorBuilder.withBody(PsiMethodUtil.createCodeBlockFromText(methodBody, constructorBuilder));
    target.add(constructorBuilder);
  }
}
