package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.lang.ASTNode;

/**
 *  @author dsl
 */
public class ImplementsListElement extends ReferenceListElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ImplementsListElement");

  public ImplementsListElement() {
    super(IMPLEMENTS_LIST);
  }

  protected String getKeywordText() {
    return PsiKeyword.IMPLEMENTS;
  }

  protected IElementType getKeywordType() {
    return IMPLEMENTS_KEYWORD;
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.EXTENDS_KEYWORD:
        return TreeUtil.findChild(this, EXTENDS_KEYWORD);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == IMPLEMENTS_KEYWORD) {
      return ChildRole.IMPLEMENTS_KEYWORD;
    }
    else if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.REFERENCE_IN_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
