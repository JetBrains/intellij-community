package de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.problem.ProblemProcessingSink;
import de.plushnikov.intellij.plugin.problem.ProblemSink;
import de.plushnikov.intellij.plugin.processor.handler.FieldNameConstantsHandler;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import de.plushnikov.intellij.plugin.util.PsiClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Updates fields of existing inner class type in class annotated by FieldNameConstants
 *
 * @author alanachtenberg
 */
public class FieldNameConstantsPredefinedInnerClassFieldProcessor extends AbstractFieldNameConstantsProcessor {

  public FieldNameConstantsPredefinedInnerClassFieldProcessor() {
    super(PsiField.class, LombokClassNames.FIELD_NAME_CONSTANTS);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass, @Nullable String nameHint) {
    if (psiClass.getParent() instanceof PsiClass) {
      PsiClass parentClass = (PsiClass) psiClass.getParent();
      PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(parentClass, getSupportedAnnotationClasses());
      if (null != psiAnnotation && supportAnnotationVariant(psiAnnotation)) {
        ProblemProcessingSink problemBuilder = new ProblemProcessingSink();
        if (super.validate(psiAnnotation, parentClass, problemBuilder)) {
          final String typeName = FieldNameConstantsHandler.getTypeName(parentClass, psiAnnotation);
          if (typeName.equals(psiClass.getName())
            && possibleToGenerateElementNamed(nameHint, psiClass, psiAnnotation)
            && validate(psiAnnotation, parentClass, problemBuilder)) {

            List<? super PsiElement> result = new ArrayList<>();
            generatePsiElements(parentClass, psiClass, psiAnnotation, result);
            return result;
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemSink builder) {
    final String typeName = FieldNameConstantsHandler.getTypeName(psiClass, psiAnnotation);
    Optional<PsiClass> innerClass = PsiClassUtil.getInnerClassInternByName(psiClass, typeName);
    if (innerClass.isPresent()) {
      final boolean asEnum = PsiAnnotationUtil.getBooleanAnnotationValue(psiAnnotation, "asEnum", false);
      if (innerClass.get().isEnum() != asEnum) {
        builder.addErrorMessage("inspection.message.field.name.constants.inner.type", asEnum);
        return false;
      }
    }
    return true;
  }

  @Override
  protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    //do nothing
  }

  private void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiClass existingInnerClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
    final Collection<PsiField> psiFields = filterFields(psiClass, psiAnnotation);
    if (!psiFields.isEmpty()) {
      List<PsiField> newFields = FieldNameConstantsHandler.createFields(existingInnerClass, psiFields);
      target.addAll(newFields);
    }
  }
}
