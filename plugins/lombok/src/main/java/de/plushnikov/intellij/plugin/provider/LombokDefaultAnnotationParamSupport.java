package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInspection.DefaultAnnotationParamInspection;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ignore DefaultAnnotationParamInspection for lombok EqualsAndHashCode annotation and callSuper param
 */
public class LombokDefaultAnnotationParamSupport implements DefaultAnnotationParamInspection.IgnoreAnnotationParamSupport {

  @Override
  public boolean ignoreAnnotationParam(@Nullable String annotationFQN, @NotNull String annotationParameterName) {
    return LombokClassNames.ACCESSORS.equals(annotationFQN) ||
           LombokClassNames.EQUALS_AND_HASHCODE.equals(annotationFQN) && ("callSuper".equals(annotationParameterName) || "of".equals(annotationParameterName));
  }
}
