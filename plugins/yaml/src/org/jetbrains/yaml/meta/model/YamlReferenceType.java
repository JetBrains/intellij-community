/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalar;

@ApiStatus.Internal
public abstract class YamlReferenceType extends YamlScalarType {

  protected YamlReferenceType(@NotNull String typeName) {
    super(typeName);
  }

  public PsiReference @NotNull [] getReferencesFromValue(@NotNull YAMLScalar valueScalar) {
    return PsiReference.EMPTY_ARRAY;
  }
}
