package com.jetbrains.python.buildout.config.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.buildout.config.BuildoutCfgElementTypes;
import com.jetbrains.python.buildout.config.BuildoutCfgTokenTypes;
import com.jetbrains.python.buildout.config.psi.impl.*;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgPsiElement;

/**
 * @author traff
 */
public class BuildoutCfgASTFactory implements BuildoutCfgElementTypes, BuildoutCfgTokenTypes {

  public PsiElement create(ASTNode node) {
    IElementType type = node.getElementType();
    if (type == SECTION) {
      return new BuildoutCfgSection(node);
    }
    if (type == SECTION_HEADER) {
      return new BuildoutCfgSectionHeader(node);
    }
    if (type == OPTION) {
      return new BuildoutCfgOption(node);
    }
    if (type == KEY) {
      return new BuildoutCfgKey(node);
    }
    if (type == VALUE) {
      return new BuildoutCfgValue(node);
    }
    if (type == VALUE_LINE) {
      return new BuildoutCfgValueLine(node);
    }
    if (type == SECTION_NAME) {
      return new BuildoutCfgSectionHeaderName(node);
    }
    
    return new BuildoutCfgPsiElement(node);
  }
}
