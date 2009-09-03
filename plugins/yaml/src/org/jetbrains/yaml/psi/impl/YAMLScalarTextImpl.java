package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalarText;

/**
 * @author oleg
 */
public class YAMLScalarTextImpl extends YAMLPsiElementImpl implements YAMLScalarText {
  public YAMLScalarTextImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML scalar text";
  }
}