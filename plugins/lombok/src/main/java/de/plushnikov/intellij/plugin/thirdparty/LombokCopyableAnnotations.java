package de.plushnikov.intellij.plugin.thirdparty;

public enum LombokCopyableAnnotations {
  BASE_COPYABLE(LombokUtils.BASE_COPYABLE_ANNOTATIONS),
  COPY_TO_SETTER(LombokUtils.COPY_TO_SETTER_ANNOTATIONS),
  COPY_TO_BUILDER_SINGULAR_SETTER(LombokUtils.COPY_TO_BUILDER_SINGULAR_SETTER_ANNOTATIONS),
  JACKSON_COPY_TO_BUILDER(LombokUtils.JACKSON_COPY_TO_BUILDER_ANNOTATIONS);

  private final String[] fullyQualifiedNames;

  LombokCopyableAnnotations(String[] fullyQualifiedNames) {
    this.fullyQualifiedNames = fullyQualifiedNames;
  }

  public String[] getFullyQualifiedNames() {
    return fullyQualifiedNames;
  }
}
