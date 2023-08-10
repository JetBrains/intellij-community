// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.ArrayList;
import java.util.List;

public final class YamlArrayAdapter implements JsonArrayValueAdapter {
  @NotNull private final YAMLSequence myArray;
  @NotNull private final NotNullLazyValue<List<JsonValueAdapter>> myChildAdapters = NotNullLazyValue.lazy(this::computeChildAdapters);

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
    PsiElement tag = myArray.getTag();
    return tag == null || "!!seq".equals(tag.getText());
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

  @Override
  public @NotNull JsonArrayValueAdapter getAsArray() {
    return this;
  }

  @NotNull
  private List<JsonValueAdapter> computeChildAdapters() {
    List<YAMLSequenceItem> items = myArray.getItems();
    List<JsonValueAdapter> adapters = new ArrayList<>(items.size());
    for (YAMLSequenceItem item: items) {
      YAMLValue value = item.getValue();
      if (value == null) {
        JsonValueAdapter emptyAdapter = YamlPropertyAdapter.createEmptyValueAdapter(item.getFirstChild(), true);
        if (emptyAdapter != null) adapters.add(emptyAdapter);
        continue;
      }
      adapters.add(YamlPropertyAdapter.createValueAdapterByType(value));
    }
    return adapters;
  }
}
