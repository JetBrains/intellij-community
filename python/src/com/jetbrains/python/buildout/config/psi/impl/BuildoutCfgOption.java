package com.jetbrains.python.buildout.config.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class BuildoutCfgOption extends BuildoutCfgPsiElement {
  public BuildoutCfgOption(@NotNull final ASTNode node) {
    super(node);
  }

  @Nullable
  public String getKey() {
    BuildoutCfgKey key = PsiTreeUtil.findChildOfType(this, BuildoutCfgKey.class);
    String result = key != null ? key.getText() : null;

    return result != null ? result.trim() : null;
  }

  public List<String> getValues() {
    List<String> result = Lists.newArrayList();
    Collection<BuildoutCfgValueLine> lines = PsiTreeUtil.collectElementsOfType(this, BuildoutCfgValueLine.class);
    for (BuildoutCfgValueLine line : lines) {
      String text = line.getText();
      if (text != null) {
        result.add(text.trim());
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return "BuildoutCfgOption:" + getNode().getElementType().toString();
  }

}
