package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import de.plushnikov.intellij.plugin.lombokconfig.ConfigDiscovery;
import de.plushnikov.intellij.plugin.lombokconfig.LombokNullAnnotationLibrary;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightParameter;
import org.jetbrains.annotations.NotNull;

public final class LombokAddNullAnnotations {
  public static LombokLightMethodBuilder createRelevantNonNullAnnotation(@NotNull PsiClass psiClass, @NotNull LombokLightMethodBuilder methodBuilder) {
    final LombokNullAnnotationLibrary annotationLibrary = ConfigDiscovery.getInstance().getAddNullAnnotationLombokConfigProperty(psiClass);
    return createRelevantNonNullAnnotation(annotationLibrary, methodBuilder);
  }

  public static LombokLightMethodBuilder createRelevantNonNullAnnotation(@NotNull LombokNullAnnotationLibrary annotationLibrary,
                                                                         @NotNull LombokLightMethodBuilder methodBuilder) {
    if (StringUtil.isNotEmpty(annotationLibrary.getNonNullAnnotation())) {
      methodBuilder.withAnnotation(annotationLibrary.getNonNullAnnotation());
    }
    return methodBuilder;
  }

  public static LombokLightFieldBuilder createRelevantNonNullAnnotation(@NotNull PsiClass psiClass, @NotNull LombokLightFieldBuilder fieldBuilder) {
    final LombokNullAnnotationLibrary annotationLibrary = ConfigDiscovery.getInstance().getAddNullAnnotationLombokConfigProperty(psiClass);
    if (StringUtil.isNotEmpty(annotationLibrary.getNonNullAnnotation())) {
      fieldBuilder.withAnnotation(annotationLibrary.getNonNullAnnotation());
    }
    return fieldBuilder;
  }

  public static LombokLightParameter createRelevantNullableAnnotation(@NotNull PsiClass psiClass,
                                                                      @NotNull LombokLightParameter lightParameter) {
    final LombokNullAnnotationLibrary annotationLibrary = ConfigDiscovery.getInstance().getAddNullAnnotationLombokConfigProperty(psiClass);
    if (StringUtil.isNotEmpty(annotationLibrary.getNullableAnnotation())) {
      lightParameter.withAnnotation(annotationLibrary.getNullableAnnotation());
    }
    return lightParameter;
  }
}
