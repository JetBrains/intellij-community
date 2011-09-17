package de.plushnikov.intellij.lombok.processor.clazz;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Base lombok processor class for constructor processing
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractConstructorClassProcessor extends AbstractLombokClassProcessor {

  protected AbstractConstructorClassProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    super(supportedAnnotation, supportedClass);
  }

  @NotNull
  protected Collection<PsiField> getAllNotInitializedAndNotStaticFields(@NotNull PsiClass psiClass) {
    Collection<PsiField> allNotInitializedNotStaticFields = new ArrayList<PsiField>();
    for (PsiField psiField : psiClass.getFields()) {
      boolean addField = false;
      // skip initialized fields
      if (null == psiField.getInitializer()) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (null != modifierList) {
          // skip static fields
          addField = !modifierList.hasModifierProperty(PsiModifier.STATIC);
        }
      }

      if (addField) {
        allNotInitializedNotStaticFields.add(psiField);
      }
    }
    return allNotInitializedNotStaticFields;
  }


  @NotNull
  protected PsiMethod createConstructorMethod(@NotNull String methodVisibility, @NotNull Collection<PsiField> params, @NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(methodVisibility);
      if (builder.length() > 0) {
        builder.append(' ');
      }
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

      return elementFactory.createMethodFromText(builder.toString(), psiClass);
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
