// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.yaml.psi.YamlTagRecogniser;

import java.util.ArrayList;
import java.util.List;

public final class YamlArrayAdapter implements JsonArrayValueAdapter {
  private final @NotNull YAMLSequence myArray;
  private final @NotNull NotNullLazyValue<List<JsonValueAdapter>> myChildAdapters = NotNullLazyValue.lazy(this::computeChildAdapters);

  public YamlArrayAdapter(@NotNull YAMLSequence array) {myArray = array;}

  @Override
  public @NotNull List<JsonValueAdapter> getElements() {
    return myChildAdapters.getValue();
  }

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public boolean isArray() {
    PsiElement tag = myArray.getTag();
    if (tag == null) return true;

    String tagText = tag.getText();
    return "!!seq".equals(tagText)
           || ContainerUtil.exists(YamlTagRecogniser.EP_NAME.getExtensionList(), extension -> extension.isRecognizedTag(tagText));
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
  public @NotNull PsiElement getDelegate() {
    return myArray;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getAsObject() {
    return null;
  }

  @Override
  public @NotNull JsonArrayValueAdapter getAsArray() {
    return this;
  }

  private @NotNull List<JsonValueAdapter> computeChildAdapters() {
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
