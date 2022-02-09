// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class YamlObjectAdapter implements JsonObjectValueAdapter {
  @NotNull private final YAMLMapping myObject;
  @NotNull private final NotNullLazyValue<List<JsonPropertyAdapter>> myChildAdapters = NotNullLazyValue.lazy(this::computeChildAdapters);

  public YamlObjectAdapter(@NotNull YAMLMapping object) {myObject = object;}

  @Override
  public boolean isObject() {
    PsiElement tag = myObject.getTag();
    return tag == null || "!!map".equals(tag.getText());
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

  @Override
  public @NotNull JsonObjectValueAdapter getAsObject() {
    return this;
  }

  @Nullable
  @Override
  public JsonArrayValueAdapter getAsArray() {
    return null;
  }

  @NotNull
  @Override
  public List<JsonPropertyAdapter> getPropertyList() {
    return myChildAdapters.getValue();
  }

  @NotNull
  private List<JsonPropertyAdapter> computeChildAdapters() {
    Collection<YAMLKeyValue> keyValues = myObject.getKeyValues();
    List<JsonPropertyAdapter> adapters = new ArrayList<>(keyValues.size());
    for (YAMLKeyValue value : keyValues) {
      if (addPropertiesFromReferencedObject(adapters, value)) continue;
      adapters.add(new YamlPropertyAdapter(value));
    }
    return adapters;
  }

  private boolean addPropertiesFromReferencedObject(List<JsonPropertyAdapter> adapters, YAMLKeyValue value) {
    String keyText = value.getKeyText();
    if (!"<<".equals(keyText)) return false;
    YAMLValue yamlValue = value.getValue();
    PsiElement resolved = resolveYamlAlias(yamlValue);
    if (resolved != null) {
      YAMLMapping mapping = ObjectUtils.tryCast(resolved, YAMLMapping.class);
      if (mapping == null) return false;
      List<JsonPropertyAdapter> propertyAdapters =
        RecursionManager.doPreventingRecursion(myObject, false, () -> new YamlObjectAdapter(mapping).getPropertyList());
      if (propertyAdapters != null) {
        adapters.addAll(propertyAdapters);
        return true;
      }
    }
    if (yamlValue instanceof YAMLMapping) {
      if (PsiTreeUtil.getChildOfType(yamlValue, YAMLAnchor.class) == null) return false;
      adapters.addAll(new YamlObjectAdapter((YAMLMapping)yamlValue).getPropertyList());
      return true;
    }
    return false;
  }

  @Nullable
  static PsiElement resolveYamlAlias(YAMLValue yamlValue) {
    PsiReference reference = yamlValue instanceof YAMLAlias ? yamlValue.getReference() : null;
    PsiElement resolved = reference == null ? null : reference.resolve();
    resolved = resolved == null ? null : resolved.getParent();
    return resolved;
  }
}
