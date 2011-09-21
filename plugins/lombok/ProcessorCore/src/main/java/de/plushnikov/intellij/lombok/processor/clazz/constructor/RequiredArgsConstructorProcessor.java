package de.plushnikov.intellij.lombok.processor.clazz.constructor;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import lombok.RequiredArgsConstructor;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class RequiredArgsConstructorProcessor extends AbstractConstructorClassProcessor {

  private static final String CLASS_NAME = RequiredArgsConstructor.class.getName();

  public RequiredArgsConstructorProcessor() {
    super(CLASS_NAME, PsiMethod.class);
  }

  protected <Psi extends PsiElement> void processIntern(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<Psi> target) {
    final String methodVisibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    if (null != methodVisibility) {
      target.addAll((Collection<? extends Psi>) createRequiredArgsConstructor(psiClass, methodVisibility, psiAnnotation));
    }
  }

  @NotNull
  public Collection<PsiMethod> createRequiredArgsConstructor(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation) {
    Collection<PsiField> allReqFields = getRequiredFields(psiClass);

    return createConstructorMethod(psiClass, methodVisibility, psiAnnotation, allReqFields);
  }

  @NotNull
  protected Collection<PsiField> getRequiredFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> result = new ArrayList<PsiField>();
    for (PsiField psiField : getAllNotInitializedAndNotStaticFields(psiClass)) {
      boolean addField = false;
      // skip initialized fields
      if (null == psiField.getInitializer()) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (null != modifierList) {
          // take only final or @NotNull fields
          boolean isFinal = modifierList.hasModifierProperty(PsiModifier.FINAL);
          boolean isNonNull = false;
          for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
            final String qualifiedName = StringUtil.notNullize(StringUtil.toLowerCase(psiAnnotation.getQualifiedName()));
            final int idx = qualifiedName.lastIndexOf(".");
            final String annotationName = idx == -1 ? qualifiedName : qualifiedName.substring(idx + 1);
            isNonNull |= TransformationsUtil.NON_NULL_PATTERN.matcher(annotationName).matches();
          }

          addField = isFinal || isNonNull;
        }
      }

      if (addField) {
        result.add(psiField);
      }
    }
    return result;
  }

}
