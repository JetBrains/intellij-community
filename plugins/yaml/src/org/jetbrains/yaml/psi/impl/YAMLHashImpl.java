package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLHash;

/**
 * @author oleg
 */
public class YAMLHashImpl extends YAMLPsiElementImpl implements YAMLHash {
  public YAMLHashImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML hash";
  }
}