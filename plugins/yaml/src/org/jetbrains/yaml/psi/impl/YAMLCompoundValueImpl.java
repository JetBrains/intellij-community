package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLCompoundValue;

/**
 * @author oleg
 */
public class YAMLCompoundValueImpl extends YAMLPsiElementImpl implements YAMLCompoundValue {
  public YAMLCompoundValueImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML compound value";
  }
}