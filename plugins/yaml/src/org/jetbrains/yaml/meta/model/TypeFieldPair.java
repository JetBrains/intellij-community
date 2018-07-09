// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.meta.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class TypeFieldPair {
  @NotNull
  private final Field myField;
  @NotNull
  private final YamlMetaClass myOwnerClass;

  public TypeFieldPair(@NotNull YamlMetaClass ownerClass, @NotNull Field field) {
    myField = field;
    myOwnerClass = ownerClass;
  }

  @NotNull
  public YamlMetaType getMetaType() {
    return myOwnerClass;
  }

  @NotNull
  public Field getField() {
    return myField;
  }
}
