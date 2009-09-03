package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLArray;

/**
 * @author oleg
 */
public class YAMLArrayImpl extends YAMLPsiElementImpl implements YAMLArray {
  public YAMLArrayImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML array";
  }
}