// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.Collection;
import java.util.List;

public class YamlObjectAdapter implements JsonObjectValueAdapter {

  @NotNull private final YAMLMapping myObject;
  @NotNull private final NotNullLazyValue<List<JsonPropertyAdapter>> myChildAdapters = new NotNullLazyValue<List<JsonPropertyAdapter>>() {
    @NotNull
    @Override
    protected List<JsonPropertyAdapter> compute() {
      return computeChildAdapters();
    }
  };

  public YamlObjectAdapter(@NotNull YAMLMapping object) {myObject = object;}

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
  public PsiElement getDelegate() {
    return myObject;
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
  public boolean shouldCheckIntegralRequirements() {
    return true;
  }

  @NotNull
  @Override
  public List<JsonPropertyAdapter> getPropertyList() {
    return myChildAdapters.getValue();
  }

  @NotNull
  private List<JsonPropertyAdapter> computeChildAdapters() {
    Collection<YAMLKeyValue> keyValues = myObject.getKeyValues();
    List<JsonPropertyAdapter> adapters = ContainerUtil.newArrayListWithCapacity(keyValues.size());
    for (YAMLKeyValue value : keyValues) {
      adapters.add(new YamlPropertyAdapter(value));
    }
    return adapters;
  }
}
