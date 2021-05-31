package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.text.StringUtil;

import java.util.HashMap;
import java.util.Map;

public enum LombokCopyableAnnotations {
  BASE_COPYABLE(LombokUtils.BASE_COPYABLE_ANNOTATIONS),
  COPY_TO_SETTER(LombokUtils.COPY_TO_SETTER_ANNOTATIONS),
  COPY_TO_BUILDER_SINGULAR_SETTER(LombokUtils.COPY_TO_BUILDER_SINGULAR_SETTER_ANNOTATIONS),
  JACKSON_COPY_TO_BUILDER(LombokUtils.JACKSON_COPY_TO_BUILDER_ANNOTATIONS);

  private final Map<String, String> fullQualifiedToShortNames;

  LombokCopyableAnnotations(String[] fullQualifiedNames) {
    fullQualifiedToShortNames = new HashMap<>();
    for (String qualifiedName : fullQualifiedNames) {
      fullQualifiedToShortNames.put(qualifiedName, StringUtil.getShortName(qualifiedName));
    }
  }

  public Map<String, String> getFullQualifiedToShortNames() {
    return new HashMap<>(fullQualifiedToShortNames);
  }
}
