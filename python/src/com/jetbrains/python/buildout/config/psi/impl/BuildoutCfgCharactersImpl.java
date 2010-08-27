package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgCharactersImpl extends BuildoutCfgPsiElementImpl{
  public BuildoutCfgCharactersImpl(@NotNull final ASTNode node) {
    super(node);
  }

  public String getCharacters() {
    return null;
  }
  

  @Override
  public String toString() {
    return "BuildoutCfgCharactersImpl:" + getNode().getElementType().toString();
  }
}
