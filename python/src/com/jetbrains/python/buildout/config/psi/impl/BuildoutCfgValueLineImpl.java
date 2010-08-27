package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgValueLineImpl extends BuildoutCfgPsiElementImpl{
  public BuildoutCfgValueLineImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "BuildoutCfgValueImpl:" + getNode().getElementType().toString();
  }
}
