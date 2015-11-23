package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.Collection;

public abstract class YAMLMappingImpl extends YAMLCompoundValueImpl implements YAMLMapping {
  public YAMLMappingImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public Collection<YAMLKeyValue> getKeyValues() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, YAMLKeyValue.class);
  }

  @Nullable
  @Override
  public YAMLKeyValue getKeyValueByKey(@NotNull String keyText) {
    for (YAMLKeyValue keyValue : getKeyValues()) {
      if (keyText.equals(keyValue.getKeyText())) {
        return keyValue;
      }
    }
    return null;
  }

  @Override
  public void putKeyValue(@NotNull YAMLKeyValue keyValueToAdd) {
    final YAMLKeyValue existingKey = getKeyValueByKey(keyValueToAdd.getKeyText());
    if (existingKey == null) {
      add(keyValueToAdd);
    }
    else {
      existingKey.replace(keyValueToAdd);
    }
  }

  @Override
  public String toString() {
    return "YAML mapping";
  }

  @NotNull
  @Override
  public String getTextValue() {
    return "<mapping:" + Integer.toHexString(getText().hashCode()) + ">";
  }
}
