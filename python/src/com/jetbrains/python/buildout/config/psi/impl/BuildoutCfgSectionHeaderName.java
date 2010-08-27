package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgSectionHeaderName extends BuildoutCfgPsiElement {
  public BuildoutCfgSectionHeaderName(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "BuildoutCfgSectionHeaderName:" + getNode().getElementType().toString();
  }
}
