package com.jetbrains.python.buildout.config.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.buildout.config.psi.BuildoutCfgPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLPsiElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgPsiElementImpl extends ASTWrapperPsiElement implements BuildoutCfgPsiElement {
  public BuildoutCfgPsiElementImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "Buildout.cfg element";
  }
}
