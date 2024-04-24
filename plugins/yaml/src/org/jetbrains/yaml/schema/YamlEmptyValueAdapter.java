// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class YamlEmptyValueAdapter implements JsonValueAdapter {
  private final PsiElement myElement;

  public YamlEmptyValueAdapter(PsiElement element) {
    myElement = element;
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
    return false;
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
    return true;
  }

  @Override
  public boolean isEmptyAdapter() {
    return true;
  }

  @Override
  public @NotNull PsiElement getDelegate() {
    return myElement;
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
