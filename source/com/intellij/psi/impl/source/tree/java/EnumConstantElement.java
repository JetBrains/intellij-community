package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.lang.ASTNode;

/**
 * @author dsl
 */
public class EnumConstantElement extends RepositoryTreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.EnumConstantElement");
  public EnumConstantElement() {
    super(ENUM_CONSTANT);
  }

  public int getTextOffset() {
    return findChildByRole(ChildRole.NAME).getStartOffset();
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.DOC_COMMENT:
        return PsiImplUtil.findDocComment(this);

      case ChildRole.NAME:
        return TreeUtil.findChild(this, JavaTokenType.IDENTIFIER);

      case ChildRole.ARGUMENT_LIST:
        return TreeUtil.findChild(this, EXPRESSION_LIST);

      case ChildRole.ANONYMOUS_CLASS:
        return TreeUtil.findChild(this, ENUM_CONSTANT_INITIALIZER);

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, JavaElementType.MODIFIER_LIST);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.DOC_COMMENT || i == JavaDocElementType.DOC_COMMENT) {
      return getChildRole(child, ChildRole.DOC_COMMENT);
    }
    else if (i == JavaTokenType.C_STYLE_COMMENT || i == JavaTokenType.END_OF_LINE_COMMENT) {
      {
        return ChildRole.NONE;
      }
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == ENUM_CONSTANT_INITIALIZER) {
      return ChildRole.ANONYMOUS_CLASS;
    }
    else if (i == EXPRESSION_LIST) {
      return ChildRole.ARGUMENT_LIST;
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
