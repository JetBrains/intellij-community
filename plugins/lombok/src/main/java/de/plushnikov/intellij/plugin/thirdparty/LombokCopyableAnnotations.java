package de.plushnikov.intellij.plugin.thirdparty;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collections;
import java.util.Map;

public enum LombokCopyableAnnotations {
  BASE_COPYABLE(LombokUtils.BASE_COPYABLE_ANNOTATIONS),
  COPY_TO_SETTER(LombokUtils.COPY_TO_SETTER_ANNOTATIONS),
  COPY_TO_BUILDER_SINGULAR_SETTER(LombokUtils.COPY_TO_BUILDER_SINGULAR_SETTER_ANNOTATIONS),
  JACKSON_COPY_TO_BUILDER(LombokUtils.JACKSON_COPY_TO_BUILDER_ANNOTATIONS);

  private final Map<String, String> fullQualifiedToShortNames;

  LombokCopyableAnnotations(String[] fqns) {
    fullQualifiedToShortNames =
      Collections.unmodifiableMap(ContainerUtil.map2Map(fqns, fqn -> Pair.create(fqn, StringUtil.getShortName(fqn))));
  }

  public Map<String, String> getFullQualifiedToShortNames() {
    return fullQualifiedToShortNames;
  }
}
