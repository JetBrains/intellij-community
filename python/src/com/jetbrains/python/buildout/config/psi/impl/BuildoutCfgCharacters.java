package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgCharacters extends BuildoutCfgPsiElement {
  public BuildoutCfgCharacters(@NotNull final ASTNode node) {
    super(node);
  }

  public String getCharacters() {
    return null;
  }
  

  @Override
  public String toString() {
    return "BuildoutCfgCharacters:" + getNode().getElementType().toString();
  }
}
