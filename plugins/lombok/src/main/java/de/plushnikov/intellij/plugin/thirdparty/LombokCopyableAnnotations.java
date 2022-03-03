package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.text.StringUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public enum LombokCopyableAnnotations {
  BASE_COPYABLE(LombokUtils.BASE_COPYABLE_ANNOTATIONS),
  COPY_TO_SETTER(LombokUtils.COPY_TO_SETTER_ANNOTATIONS),
  COPY_TO_BUILDER_SINGULAR_SETTER(LombokUtils.COPY_TO_BUILDER_SINGULAR_SETTER_ANNOTATIONS),
  JACKSON_COPY_TO_BUILDER(LombokUtils.JACKSON_COPY_TO_BUILDER_ANNOTATIONS);

  private final Map<String, Set<String>> shortNames;

  LombokCopyableAnnotations(String[] fqns) {
    shortNames = new HashMap<>(fqns.length);
    for (String fqn : fqns) {
      String shortName = StringUtil.getShortName(fqn);
      shortNames.computeIfAbsent(shortName, __ -> new HashSet<>(5)).add(fqn);
    }
  }
  public Map<String, Set<String>> getShortNames() {
    return shortNames;
  }
}
