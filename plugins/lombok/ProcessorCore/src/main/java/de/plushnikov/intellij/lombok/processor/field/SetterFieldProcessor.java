package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.util.PsiAnnotationUtil;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import lombok.Setter;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Plushnikov Michail
 */
public class SetterFieldProcessor extends AbstractLombokFieldProcessor {

  public static final String CLASS_NAME = Setter.class.getName();

  public SetterFieldProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getMethodVisibility(psiAnnotation);
    if (methodVisibility != null) {
      target.add((Psi) createSetterMethod(psiField, methodVisibility));
    }
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result;
    result = validateFinalModifier(psiField, builder);
    if (result) {
      result = validateVisibility(psiAnnotation);
      if (result) {
        result = validateExistingMethods(psiField, builder);
      }
    }
    return result;
  }

  protected boolean validateFinalModifier(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiField.hasModifierProperty(PsiModifier.FINAL)) {
      builder.addError("@Setter on final filed is not allowed");
      result = false;
    }
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String methodVisibity = LombokProcessorUtil.getMethodVisibility(psiAnnotation);
    return null != methodVisibity;
  }

  protected boolean validateExistingMethods(@NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;
    final PsiClass psiClass = psiField.getContainingClass();
    if (null != psiClass) {
      final PsiMethod[] classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
      final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
      final Collection<String> methodNames = TransformationsUtil.toAllSetterNames(psiField.getName(), isBoolean);

      for (String methodName : methodNames) {
        if (PsiMethodUtil.hasMethodByName(classMethods, methodName)) {
          final String setterMethodName = TransformationsUtil.toSetterName(psiField.getName(), isBoolean);

          builder.addProblem(String.format("Not generated '%s'(): A method with similar name '%s' already exists", setterMethodName, methodName));
          result = false;
        }
      }
    }
    return result;
  }

  @NotNull
  public PsiMethod createSetterMethod(@NotNull PsiField psiField, @NotNull String methodVisibility) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      final String fieldName = psiField.getName();
      final PsiType psiFieldType = psiField.getType();
      final String methodName = TransformationsUtil.toSetterName(fieldName, PsiType.BOOLEAN.equals(psiFieldType));

      builder.append(methodVisibility);
      if (builder.length() > 0) {
        builder.append(' ');
      }
      if (psiField.hasModifierProperty(PsiModifier.STATIC)) {
        builder.append(PsiModifier.STATIC).append(' ');
      }
      builder.append(PsiType.VOID.getCanonicalText());
      builder.append(' ');
      builder.append(methodName);

      final Collection<String> annotationsToCopy = PsiAnnotationUtil.collectAnnotationsToCopy(psiField);
      final String annotationsString = PsiAnnotationUtil.buildAnnotationsString(annotationsToCopy);

      builder.append("(").append(annotationsString).append(psiFieldType.getCanonicalText()).append(' ').append(fieldName).append(')');
      builder.append("{ this.").append(fieldName).append(" = ").append(fieldName).append("; }");

      PsiClass psiClass = psiField.getContainingClass();
      assert psiClass != null;

      UserMapKeys.addWriteUsageFor(psiField);

      return PsiMethodUtil.createMethod(psiClass, builder.toString(), psiField);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


}
