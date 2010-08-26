package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgSectionHeaderNameImpl extends BuildoutCfgPsiElementImpl{
  public BuildoutCfgSectionHeaderNameImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "BuildoutCfgSectionHeaderNameImpl:" + getNode().getElementType().toString();
  }
}
