// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public List<JsonPropertyAdapter> getPropertyList() {
    return ContainerUtil.emptyList();
  }

  @NotNull
  @Override
  public PsiElement getDelegate() {
    return myElement;
  }

  @Nullable
  @Override
  public JsonObjectValueAdapter getAsObject() {
    return this;
  }

  @Nullable
  @Override
  public JsonArrayValueAdapter getAsArray() {
    return null;
  }

  @Override
  public JsonSchemaType getAlternateType(JsonSchemaType type) {
    return type == JsonSchemaType._object ? JsonSchemaType._null : type;
  }
}
