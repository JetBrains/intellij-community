package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

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
      addNewKey(keyValueToAdd);
    }
    else {
      existingKey.replace(keyValueToAdd);
    }
  }

  @Override
  public void deleteKeyValue(@NotNull YAMLKeyValue keyValueToDelete) {
    if (keyValueToDelete.getParent() != this) {
      throw new IllegalArgumentException("KeyValue should be the child of this");
    }

    if (keyValueToDelete.getPrevSibling() != null) {
      while (keyValueToDelete.getPrevSibling() != null && !(keyValueToDelete.getPrevSibling() instanceof YAMLKeyValue)) {
        keyValueToDelete.getPrevSibling().delete();
      }
    }
    else {
      while (keyValueToDelete.getNextSibling() != null && !(keyValueToDelete.getNextSibling() instanceof YAMLKeyValue)) {
        keyValueToDelete.getNextSibling().delete();
      }
    }
    keyValueToDelete.delete();
  }

  protected abstract void addNewKey(@NotNull YAMLKeyValue key);

  @Override
  public String toString() {
    return "YAML mapping";
  }

  @NotNull
  @Override
  public String getTextValue() {
    return "<mapping:" + Integer.toHexString(getText().hashCode()) + ">";
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitMapping(this);
    }
    else {
      super.accept(visitor);
    }
  }
}
