// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
  private final @NotNull YAMLMapping myObject;
  private final @NotNull NotNullLazyValue<List<JsonPropertyAdapter>> myChildAdapters = NotNullLazyValue.lazy(this::computeChildAdapters);

  public YamlObjectAdapter(@NotNull YAMLMapping object) {myObject = object;}

  @Override
  public boolean isObject() {
    PsiElement tag = myObject.getTag();

    if (tag == null) return true;
    String tagText = tag.getText();
    return "!!map".equals(tagText)
           || ContainerUtil.exists(YamlTagRecogniser.EP_NAME.getExtensionList(), extension -> extension.isRecognizedTag(tagText));
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
  public @NotNull PsiElement getDelegate() {
    return myObject;
  }

  @Override
  public @NotNull JsonObjectValueAdapter getAsObject() {
    return this;
  }

  @Override
  public @Nullable JsonArrayValueAdapter getAsArray() {
    return null;
  }

  @Override
  public @NotNull List<JsonPropertyAdapter> getPropertyList() {
    return myChildAdapters.getValue();
  }

  private @NotNull List<JsonPropertyAdapter> computeChildAdapters() {
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

  static @Nullable PsiElement resolveYamlAlias(YAMLValue yamlValue) {
    PsiReference reference = yamlValue instanceof YAMLAlias ? yamlValue.getReference() : null;
    PsiElement resolved = reference == null ? null : reference.resolve();
    resolved = resolved == null ? null : resolved.getParent();
    return resolved;
  }
}
