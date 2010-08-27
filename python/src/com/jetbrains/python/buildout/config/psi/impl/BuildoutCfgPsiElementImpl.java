package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.jetbrains.python.buildout.config.psi.BuildoutCfgPsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgPsiElementImpl extends ASTWrapperPsiElement implements BuildoutCfgPsiElement {
  public BuildoutCfgPsiElementImpl(@NotNull final ASTNode node) {
    super(node);
  }

  public List<BuildoutCfgSectionImpl> getSections() {
         return null;
  }

  @Override
  public String toString() {
    return "BuildoutCfgPsiElementImpl:" + getNode().getElementType().toString();
  }
}
