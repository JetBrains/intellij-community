// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLValue;

public class YamlPropertyAdapter implements JsonPropertyAdapter {

  private final YAMLKeyValue myProperty;

  public YamlPropertyAdapter(@NotNull YAMLKeyValue property) {myProperty = property;}

  @Nullable
  @Override
  public String getName() {
    return myProperty.getKeyText();
  }

  @Nullable
  @Override
  public JsonValueAdapter getNameValueAdapter() {
    return null; // todo: we need a separate adapter for names; but currently names schema is rarely used, let's just skip validation
  }

  @Nullable
  @Override
  public JsonValueAdapter getValue() {
    YAMLValue value = myProperty.getValue();
    return value == null ? null : createValueAdapterByType(value);
  }

  @NotNull
  @Override
  public PsiElement getDelegate() {
    return myProperty;
  }

  @Nullable
  @Override
  public JsonObjectValueAdapter getParentObject() {
    return myProperty.getParentMapping() != null ? new YamlObjectAdapter(myProperty.getParentMapping()) : null;
  }

  @NotNull
  public static JsonValueAdapter createValueAdapterByType(@NotNull YAMLValue value) {
    if (value instanceof YAMLMapping) return new YamlObjectAdapter((YAMLMapping) value);
    if (value instanceof YAMLSequence) return new YamlArrayAdapter((YAMLSequence) value);
    return new YamlGenericValueAdapter(value);
  }
}
