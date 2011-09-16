package de.plushnikov.intellij.lombok.processor.field;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.util.StringBuilderSpinAllocator;
import de.plushnikov.intellij.lombok.psi.MyLightMethod;
import lombok.handlers.TransformationsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Plushnikov Michail
 */
public abstract class AbstractLombokFieldProcessor implements LombokFieldProcessor {

  private final String supportedAnnotation;
  private final Class supportedClass;

  protected AbstractLombokFieldProcessor(@NotNull String supportedAnnotation, @NotNull Class supportedClass) {
    this.supportedAnnotation = supportedAnnotation;
    this.supportedClass = supportedClass;
  }

  public <Psi extends PsiElement> boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<Psi> type) {
    final String annotationName = StringUtil.notNullize(psiAnnotation.getQualifiedName()).trim();
    return (supportedAnnotation.equals(annotationName) || supportedAnnotation.endsWith(annotationName)) &&
        (type.isAssignableFrom(supportedClass));
  }

  @NotNull
  protected LightMethod prepareMethod(@NotNull PsiManager manager, @NotNull PsiMethod method, @NotNull PsiClass psiClass, @NotNull PsiElement psiNavigationTarget) {
    LightMethod lightMethod = new MyLightMethod(manager, method, psiClass);
    lightMethod.setNavigationElement(psiNavigationTarget);
    return lightMethod;
  }

  protected boolean hasMethodByName(@NotNull PsiMethod[] classMethods, @NotNull PsiMethod psiMethod) {
    boolean hasMethod = false;
    for (PsiMethod classMethod : classMethods) {
      if (classMethod.getName().equals(psiMethod.getName())) {
        hasMethod = true;
        break;
      }
    }
    return hasMethod;
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
