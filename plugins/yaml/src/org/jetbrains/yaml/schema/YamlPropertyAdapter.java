// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import java.util.Collection;
import java.util.Collections;

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

  @NotNull
  @Override
  public Collection<JsonValueAdapter> getValues() {
    YAMLValue value = myProperty.getValue();
    return value == null ? ContainerUtil.emptyList() : Collections.singletonList(createValueAdapterByType(value));
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
    if (value instanceof YAMLAlias) {
      PsiElement result = YamlObjectAdapter.resolveYamlAlias(value);
      if (result instanceof YAMLValue) {
        JsonValueAdapter adapter = RecursionManager.doPreventingRecursion(value, false, () -> createValueAdapterByType((YAMLValue)result));
        if (adapter != null) return adapter;
      }
    }
    if (value instanceof YAMLMapping) return new YamlObjectAdapter((YAMLMapping) value);
    if (value instanceof YAMLSequence) return new YamlArrayAdapter((YAMLSequence) value);
    return new YamlGenericValueAdapter(value);
  }
}
