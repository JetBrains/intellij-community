package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.processor.AbstractLombokProcessor;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Base lombok processor class for field annotations
 *
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokFieldProcessor extends AbstractLombokProcessor implements LombokFieldProcessor {

  protected AbstractLombokFieldProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    super(supportedAnnotation, supportedClass);
  }

  @NotNull
  protected Collection<String> collectAnnotationsToCopy(@NotNull PsiField psiField) {
    Collection<String> annotationsToCopy = new ArrayList<String>();
    PsiModifierList modifierList = psiField.getModifierList();
    if (null != modifierList) {
      for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
        final String qualifiedName = StringUtil.notNullize(psiAnnotation.getQualifiedName());
        final String annotationName = extractAnnotationName(qualifiedName);
        if (TransformationsUtil.NON_NULL_PATTERN.matcher(annotationName).matches()) {
          annotationsToCopy.add(qualifiedName);
        }
      }
    }
    return annotationsToCopy;
  }

  @NotNull
  private String extractAnnotationName(@NotNull String qualifiedName) {
    final String annotationName;
    int indexOfLastPoint = qualifiedName.lastIndexOf('.');
    if (indexOfLastPoint != -1) {
      annotationName = qualifiedName.substring(indexOfLastPoint + 1);
    } else {
      annotationName = qualifiedName;
    }
    return annotationName;
  }

  @NotNull
  protected String buildAnnotationsString(@NotNull Collection<String> annotationsToCopy) {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      for (String annotationName : annotationsToCopy) {
        builder.append('@').append(annotationName).append(' ');
      }
      return builder.toString();
    } finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

}
