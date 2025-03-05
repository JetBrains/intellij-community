package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

public class LombokNullAnnotationLibraryCustom implements LombokNullAnnotationLibrary {
  private final String nonNullAnnotation;
  private final String nullableAnnotation;
  private final boolean typeUse;

  public LombokNullAnnotationLibraryCustom(String nonNullAnnotation, String nullableAnnotation, boolean typeUse) {
    this.nonNullAnnotation = nonNullAnnotation;
    this.nullableAnnotation = nullableAnnotation;
    this.typeUse = typeUse;
  }

  @Override
  public String getNonNullAnnotation() {
    return nonNullAnnotation;
  }

  @Override
  public String getNullableAnnotation() {
    return nullableAnnotation;
  }

  @Override
  public boolean isTypeUse() {
    return typeUse;
  }

  public static @Nullable LombokNullAnnotationLibrary parseCustom(String value) {
    if (StringUtil.toLowerCase(value).startsWith("custom:")) {
      String customConfigValue = value.substring("custom:".length());
      final boolean useType = StringUtil.toLowerCase(customConfigValue).startsWith("type_use:");
      if (useType) {
        customConfigValue = customConfigValue.substring("type_use:".length());
      }

      String nonNullAnnotation, nullableAnnotation = null;
      final int splitIndex = customConfigValue.indexOf(':');
      if (splitIndex == -1) {
        nonNullAnnotation = customConfigValue;
      }
      else {
        nonNullAnnotation = customConfigValue.substring(0, splitIndex);
        nullableAnnotation = customConfigValue.substring(splitIndex + 1);
      }

      if (verifyTypeName(nonNullAnnotation) &&
          (null == nullableAnnotation || verifyTypeName(nullableAnnotation))) {
        return new LombokNullAnnotationLibraryCustom(nonNullAnnotation, nullableAnnotation, useType);
      }
    }
    return null;
  }

  private static boolean verifyTypeName(String fqn) {
    boolean atStart = true;
    for (int i = 0; i < fqn.length(); i++) {
      char c = fqn.charAt(i);
      if (Character.isJavaIdentifierStart(c)) {
        atStart = false;
        continue;
      }
      if (atStart) return false;
      if (c == '.') {
        atStart = true;
        continue;
      }
      if (Character.isJavaIdentifierPart(c)) continue;
      return false;
    }
    return !atStart;
  }
}
