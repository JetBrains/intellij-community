// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.List;

public class YamlArrayAdapter implements JsonArrayValueAdapter {

  @NotNull private final YAMLSequence myArray;
  @NotNull private final NotNullLazyValue<List<JsonValueAdapter>> myChildAdapters = new NotNullLazyValue<List<JsonValueAdapter>>() {
    @NotNull
    @Override
    protected List<JsonValueAdapter> compute() {
      return computeChildAdapters();
    }
  };

  public YamlArrayAdapter(@NotNull YAMLSequence array) {myArray = array;}

  @NotNull
  @Override
  public List<JsonValueAdapter> getElements() {
    return myChildAdapters.getValue();
  }

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public boolean isArray() {
    return true;
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
    return myArray;
  }

  @Nullable
  @Override
  public JsonObjectValueAdapter getAsObject() {
    return null;
  }

  @Nullable
  @Override
  public JsonArrayValueAdapter getAsArray() {
    return this;
  }

  @Override
  public boolean shouldCheckIntegralRequirements() {
    return true;
  }

  @NotNull
  private List<JsonValueAdapter> computeChildAdapters() {
    List<YAMLSequenceItem> items = myArray.getItems();
    List<JsonValueAdapter> adapters = ContainerUtil.newArrayListWithCapacity(items.size());
    for (YAMLSequenceItem item: items) {
      YAMLValue value = item.getValue();
      if (value == null) continue;
      adapters.add(YamlPropertyAdapter.createValueAdapterByType(value));
    }
    return adapters;
  }
}
