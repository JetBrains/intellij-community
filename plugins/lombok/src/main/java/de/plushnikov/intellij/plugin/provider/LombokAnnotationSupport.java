// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package de.plushnikov.intellij.plugin.provider;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.annoPackages.AnnotationPackageSupport;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class LombokAnnotationSupport implements AnnotationPackageSupport {
  @NotNull
  @Override
  public List<String> getNullabilityAnnotations(@NotNull Nullability nullability) {
    switch (nullability) {
      case NOT_NULL:
        return Collections.singletonList("lombok.NonNull");
      case NULLABLE:
      default:
        return Collections.emptyList();
    }
  }
}
