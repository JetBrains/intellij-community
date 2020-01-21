// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.buildout.config.BuildoutCfgElementTypes;
import com.jetbrains.python.buildout.config.BuildoutCfgTokenTypes;
import com.jetbrains.python.buildout.config.psi.impl.*;

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
