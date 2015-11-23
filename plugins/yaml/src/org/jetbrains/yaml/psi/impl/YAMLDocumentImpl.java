package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLCompoundValue;
import org.jetbrains.yaml.psi.YAMLDocument;

/**
 * @author oleg
 */
public class YAMLDocumentImpl extends YAMLPsiElementImpl implements YAMLDocument {
  public YAMLDocumentImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML document";
  }

  @NotNull
  @Override
  public YAMLCompoundValue getCompoundValue() {
    final YAMLCompoundValue type = PsiTreeUtil.findChildOfType(this, YAMLCompoundValue.class);
    assert type != null : "If compound value if not found, parser failed!!";
    return type;
  }
}
