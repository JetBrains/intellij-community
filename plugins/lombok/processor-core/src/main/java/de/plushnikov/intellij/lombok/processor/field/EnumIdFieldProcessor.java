package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.lombok.StringUtils;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.lombok.psi.LombokPsiElementFactory;
import de.plushnikov.intellij.lombok.util.PsiClassUtil;
import lombok.EnumId;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Inspect and validate @EnumId lombok-pg annotation on a field
 * Creates findByFIELD_NAME() method for this field
 *
 * @author Plushnikov Michail
 */
public class EnumIdFieldProcessor extends AbstractLombokFieldProcessor {

  public EnumIdFieldProcessor() {
    super(EnumId.class, PsiMethod.class);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiField psiField, @NotNull ProblemBuilder builder) {
    boolean result = true;

    final PsiClass psiClass = psiField.getContainingClass();
    if (null == psiClass) {
      result = false;
    }
    result &= validateEnum(psiClass, builder);

    return result;
  }

  private boolean validateEnum(PsiClass psiClass, ProblemBuilder builder) {
    boolean result = true;
    if (!psiClass.isEnum()) {
      builder.addError(String.format("'@EnumId' can be used on enum fields only"));
      result = false;
    }
    return result;
  }

  protected void processIntern(@NotNull PsiField psiField, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final String fieldName = psiField.getName();
    final PsiType psiFieldType = psiField.getType();

    final String methodName = getFindByName(psiField);

    PsiClass psiClass = psiField.getContainingClass();
    assert psiClass != null;

    UserMapKeys.addWriteUsageFor(psiField);

    LombokLightMethodBuilder method = LombokPsiElementFactory.getInstance().createLightMethod(psiField.getManager(), methodName)
        .withMethodReturnType(PsiClassUtil.getClassType(psiClass))
        .withContainingClass(psiClass)
        .withParameter(fieldName, psiFieldType)
        .withNavigationElement(psiField);
    method.withModifier(PsiModifier.STATIC);
    method.withModifier(PsiModifier.PUBLIC);

    target.add(method);
  }

  protected String getFindByName(PsiField psiField) {
    return String.format("findBy%s", StringUtils.capitalize(psiField.getName()));
  }

}
