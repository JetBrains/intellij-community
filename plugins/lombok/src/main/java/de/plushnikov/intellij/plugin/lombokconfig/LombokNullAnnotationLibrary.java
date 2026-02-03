package de.plushnikov.intellij.plugin.lombokconfig;

public interface LombokNullAnnotationLibrary {
  String getNonNullAnnotation();

  String getNullableAnnotation();

  boolean isTypeUse();
}
