package com.jetbrains.python.buildout.config.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.buildout.config.BuildoutCfgElementTypes;
import com.jetbrains.python.buildout.config.BuildoutCfgTokenTypes;
import com.jetbrains.python.buildout.config.psi.impl.*;

/**
 * @author traff
 */
public class BuildoutCfgASTFactory implements BuildoutCfgElementTypes, BuildoutCfgTokenTypes {

  public PsiElement create(ASTNode node) {
    IElementType type = node.getElementType();
    if (type == SECTION) {
      return new BuildoutCfgSectionImpl(node);
    }
    if (type == SECTION_HEADER) {
      return new BuildoutCfgSectionHeaderImpl(node);
    }
    if (type == OPTION) {
      return new BuildoutCfgOptionImpl(node);
    }
    if (type == KEY) {
      return new BuildoutCfgKeyImpl(node);
    }
    if (type == VALUE) {
      return new BuildoutCfgValueImpl(node);
    }
    if (type == VALUE_LINE) {
      return new BuildoutCfgValueLineImpl(node);
    }
    if (type == SECTION_NAME) {
      return new BuildoutCfgSectionHeaderNameImpl(node);
    }
    //if (type == KEY_CHARACTERS || type == VALUE_CHARACTERS) {
    //  return new BuildoutCfgCharactersImpl(node);
    //}

    return new BuildoutCfgPsiElementImpl(node);
  }
}
