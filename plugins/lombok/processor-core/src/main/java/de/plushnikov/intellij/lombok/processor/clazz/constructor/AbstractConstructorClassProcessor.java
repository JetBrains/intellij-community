package de.plushnikov.intellij.lombok.processor.clazz.constructor;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.UserMapKeys;
import de.plushnikov.intellij.lombok.problem.ProblemBuilder;
import de.plushnikov.intellij.lombok.processor.LombokProcessorUtil;
import de.plushnikov.intellij.lombok.processor.clazz.AbstractLombokClassProcessor;
import de.plushnikov.intellij.lombok.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Base lombok processor class for constructor processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractConstructorClassProcessor extends AbstractLombokClassProcessor {

  protected AbstractConstructorClassProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    super(supportedAnnotation, supportedClass);
  }

  @Override
  protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (!validateAnnotationOnRigthType(psiClass, builder)) {
      result = false;
    }
    if (!validateVisibility(psiAnnotation)) {
      result = false;
    }
    //TODO implement me
    return result;
  }

  protected boolean validateVisibility(@NotNull PsiAnnotation psiAnnotation) {
    final String visibility = LombokProcessorUtil.getAccessVisibity(psiAnnotation);
    return null != visibility;
  }

  protected boolean validateAnnotationOnRigthType(@NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
    boolean result = true;
    if (psiClass.isAnnotationType() || psiClass.isInterface()) {
      builder.addError("Annotation is only supported on a class or enum type");
      result = false;
    }
    return result;
  }

  @NotNull
  protected Collection<PsiField> getAllNotInitializedAndNotStaticFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> allNotInitializedNotStaticFields = new ArrayList<PsiField>();
    for (PsiField psiField : psiClass.getFields()) {
      // skip fields named $
      boolean addField = !psiField.getName().startsWith("$");

      final PsiModifierList modifierList = psiField.getModifierList();
      if (null != modifierList) {
        // skip static fields
        addField &= !modifierList.hasModifierProperty(PsiModifier.STATIC);
        // skip initialized final fields
        addField &= !(null != psiField.getInitializer() && modifierList.hasModifierProperty(PsiModifier.FINAL));
      }

      if (addField) {
        allNotInitializedNotStaticFields.add(psiField);
      }
    }
    return allNotInitializedNotStaticFields;
  }


  @NotNull
  protected Collection<PsiMethod> createConstructorMethod(@NotNull PsiClass psiClass, @NotNull String methodVisibility, @NotNull PsiAnnotation psiAnnotation, @NotNull Collection<PsiField> params) {
    //TODO add check if constructor already exists

    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(psiClass.isEnum() ? PsiModifier.PRIVATE : methodVisibility);
      if (builder.length() > 0) {
        builder.append(' ');
      }
      //TODO append @java.beans.ConstructorProperties({"x"}) annotation
      builder.append(psiClass.getName());
      builder.append('(');
      if (!params.isEmpty()) {
        for (PsiField param : params) {
          builder.append(param.getType().getCanonicalText()).append(' ').append(param.getName()).append(',');
        }
        builder.deleteCharAt(builder.length() - 1);
      }
      builder.append(')');
      builder.append("{ super();}");

      for (PsiField psiField : params) {
        UserMapKeys.addWriteUsageFor(psiField);
      }

      return Collections.singletonList(PsiMethodUtil.createMethod(psiClass, builder.toString(), psiAnnotation));
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
