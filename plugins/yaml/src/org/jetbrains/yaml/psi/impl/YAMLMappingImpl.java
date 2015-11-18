package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;

import java.util.List;

public class YAMLMappingImpl extends YAMLCompoundValueImpl implements YAMLMapping {
  public YAMLMappingImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public List<YAMLKeyValue> getKeyValues() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, YAMLKeyValue.class);
  }

  @Override
  public String toString() {
    return "YAML mapping";
  }
}
