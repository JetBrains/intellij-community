package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.NavigatablePsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgPsiElement extends ASTWrapperPsiElement implements NavigatablePsiElement {
  public BuildoutCfgPsiElement(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "BuildoutCfgPsiElement:" + getNode().getElementType().toString();
  }
}
