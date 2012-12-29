package com.jetbrains.rest.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.rest.RestElementTypes;
import com.jetbrains.rest.RestTokenTypes;

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
