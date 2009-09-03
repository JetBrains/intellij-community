package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalarList;

/**
 * @author oleg
 */
public class YAMLScalarListImpl extends YAMLPsiElementImpl implements YAMLScalarList {
  public YAMLScalarListImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML scalar list";
  }
}