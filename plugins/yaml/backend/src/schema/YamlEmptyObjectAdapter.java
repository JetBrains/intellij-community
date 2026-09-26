// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class YamlEmptyObjectAdapter implements JsonObjectValueAdapter {
  private final PsiElement myElement;

  public YamlEmptyObjectAdapter(PsiElement element) {
    myElement = element;
  }

  @Override
  public boolean isObject() {
    return true;
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
  public boolean isEmptyAdapter() {
    return true;
  }

  @Override
  public @NotNull List<JsonPropertyAdapter> getPropertyList() {
    return ContainerUtil.emptyList();
  }

  @Override
  public @NotNull PsiElement getDelegate() {
    return myElement;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getAsObject() {
    return this;
  }

  @Override
  public @Nullable JsonArrayValueAdapter getAsArray() {
    return null;
  }

  @Override
  public JsonSchemaType getAlternateType(JsonSchemaType type) {
    return type == JsonSchemaType._object ? JsonSchemaType._null : type;
  }
}
