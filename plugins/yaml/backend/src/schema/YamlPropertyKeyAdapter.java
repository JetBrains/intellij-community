// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class YamlPropertyKeyAdapter implements JsonValueAdapter {
  private final PsiElement myDelegate;

  public YamlPropertyKeyAdapter(PsiElement delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public boolean isStringLiteral() {
    return true;
  }

  @Override
  public boolean isNumberLiteral() {
    return false;
  }

  @Override
  public boolean isBooleanLiteral() {
    return false;
  }

  @Override
  public boolean isNull() {
    return false;
  }

  @Override
  public @NotNull PsiElement getDelegate() {
    return myDelegate;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getAsObject() {
    return null;
  }

  @Override
  public @Nullable JsonArrayValueAdapter getAsArray() {
    return null;
  }
}
