package de.plushnikov.intellij.plugin.lombokconfig;

/**
 * Based on <a href="https://github.com/projectlombok/lombok/blob/master/src/core/lombok/core/configuration/NullAnnotationLibrary.java">Lombok's NullAnnotationLibrary</a>
 */
public enum LombokNullAnnotationLibraryDefned implements LombokNullAnnotationLibrary {
  NONE("none", null, null, false),
  JAVAX("javax", "javax.annotation.Nonnull", "javax.annotation.Nullable", false),
  JAKARTA("jakarta", "jakarta.annotation.Nonnull", "jakarta.annotation.Nullable", false),
  ECLIPSE("eclipse", "org.eclipse.jdt.annotation.NonNull", "org.eclipse.jdt.annotation.Nullable", true),
  JETBRAINS("jetbrains", "org.jetbrains.annotations.NotNull", "org.jetbrains.annotations.Nullable", false),
  NETBEANS("netbeans", "org.netbeans.api.annotations.common.NonNull", "org.netbeans.api.annotations.common.NullAllowed", false),
  ANDROIDX("androidx", "androidx.annotation.NonNull", "androidx.annotation.Nullable", false),
  ANDROID_SUPPORT("android.support", "android.support.annotation.NonNull", "android.support.annotation.Nullable", false),
  CHECKERFRAMEWORK("checkerframework", "org.checkerframework.checker.nullness.qual.NonNull",
                   "org.checkerframework.checker.nullness.qual.Nullable", true),
  FINDBUGS("findbugs", "edu.umd.cs.findbugs.annotations.NonNull", "edu.umd.cs.findbugs.annotations.Nullable", false),
  SPRING("spring", "org.springframework.lang.NonNull", "org.springframework.lang.Nullable", false),
  JML("jml", "org.jmlspecs.annotation.NonNull", "org.jmlspecs.annotation.Nullable", false);


  private final String key;
  private final String nonNullAnnotation;
  private final String nullableAnnotation;
  private final boolean typeUse;

  LombokNullAnnotationLibraryDefned(String key, String nonNullAnnotation, String nullableAnnotation, boolean typeUse) {
    this.key = key;
    this.nonNullAnnotation = nonNullAnnotation;
    this.nullableAnnotation = nullableAnnotation;
    this.typeUse = typeUse;
  }

  public String getKey() {
    return key;
  }

  /**
   * Returns the fully qualified annotation name to apply to non-null elements. If {@code null} is returned, apply no annotation.
   */
  @Override
  public String getNonNullAnnotation() {
    return nonNullAnnotation;
  }

  /**
   * Returns the fully qualified annotation name to apply to nullable elements. If {@code null} is returned, apply no annotation.
   */
  @Override
  public String getNullableAnnotation() {
    return nullableAnnotation;
  }

  /**
   * If {@code true}, the annotation can only be used in TYPE_USE form, otherwise, prefer to annotate the parameter, not the type of the parameter (or the method, not the return type, etc).
   */
  @Override
  public boolean isTypeUse() {
    return typeUse;
  }
}
