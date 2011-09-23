package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiFieldUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
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

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    final boolean result = validateAnnotationOnRigthType(psiClass, builder);
    if (result) {
      validateExistingMethods(psiClass, builder);
    }

    // TODO validation messages for
    //validate exclude : This field does not exist, or would have been excluded anyway
    //validate of : This field does not exist
    //validate : exclude and of are mutually exclusive; the 'exclude' parameter will be ignored.
    //validate : Generating toString() implementation but without a call to superclass, even though this class does not extend java.lang.Object. If this is intentional, add '@EqualsAndHashCode(callSuper=false)' to your type.
    return result;
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("@ToString is only supported on a class or enum type");
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

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    target.addAll((Collection<? extends Psi>) createToStringMethod(psiClass, psiAnnotation));
  }

  @NotNull
  public Collection<PsiMethod> createToStringMethod(@NotNull PsiClass psiClass, @NotNull PsiElement psiNavTargetElement) {
    final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    if (PsiMethodUtil.hasMethodByName(classMethods, METHOD_NAME)) {
      return Collections.emptyList();
    }

    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("@java.lang.Override ");
      builder.append("public java.lang.String ").append(METHOD_NAME).append("()");
      builder.append("{ return super.toString(); }");

      Collection<PsiField> toStringFields = PsiFieldUtil.filterFieldsByModifiers(psiClass.getFields(), PsiModifier.STATIC);
      UserMapKeys.addReadUsageFor(toStringFields);

      return Collections.singletonList(PsiMethodUtil.createMethod(psiClass, builder.toString(), psiNavTargetElement));
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

}
