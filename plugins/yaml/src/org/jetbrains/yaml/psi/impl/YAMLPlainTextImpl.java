package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class YAMLPlainTextImpl extends YAMLPsiElementImpl {
  public YAMLPlainTextImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML plain scalar text";
  }
}
