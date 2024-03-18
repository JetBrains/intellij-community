// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.python.reStructuredText.RestElementTypes;
import com.intellij.python.reStructuredText.RestTokenTypes;

/**
 * User : catherine
 */
public class RestASTFactory implements RestTokenTypes, RestElementTypes {

  public PsiElement create(ASTNode node) {
    IElementType type = node.getElementType();

    if (type == DIRECTIVE_BLOCK) {
      return new RestDirectiveBlock(node);
    }
    if (type == REFERENCE_NAME) {
      return new RestReference(node);
    }
    if (type == REFERENCE_TARGET) {
      return new RestReferenceTarget(node);
    }
    if (type == TITLE) {
      return new RestTitle(node);
    }
    if (type == FIELD) {
      return new RestRole(node);
    }
    if (type == FIELD_LIST) {
      return new RestFieldList(node);
    }
    if (type == INLINE_BLOCK) {
      return new RestInlineBlock(node);
    }
    if (type == LINE_TEXT) {
      return new RestLine(node);
    }
    return new ASTWrapperPsiElement(node);
  }
}
