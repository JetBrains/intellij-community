package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgSectionHeaderImpl extends BuildoutCfgPsiElementImpl{
  public BuildoutCfgSectionHeaderImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Nullable
  public String getName() {
    String name =  getText();
    return  name != null? name.trim(): null;
  }

  @Override
  public String toString() {
    return "BuildoutCfgSectionHeaderImpl:" + getNode().getElementType().toString();
  }
}
