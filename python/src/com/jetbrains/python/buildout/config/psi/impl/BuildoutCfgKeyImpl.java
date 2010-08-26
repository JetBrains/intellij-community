package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgKeyImpl extends BuildoutCfgPsiElementImpl{
  public BuildoutCfgKeyImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "BuildoutCfgKeyImpl:" + getNode().getElementType().toString();
  }
}
