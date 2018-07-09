/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.yaml.meta.model;

import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalar;

@ApiStatus.Experimental
public abstract class YamlReferenceType extends YamlScalarType {

  protected YamlReferenceType(@NotNull String typeName) {
    super(typeName);
  }

  @NotNull
  public PsiReference[] getReferencesFromValue(@NotNull YAMLScalar valueScalar) {
    return PsiReference.EMPTY_ARRAY;
  }
}
