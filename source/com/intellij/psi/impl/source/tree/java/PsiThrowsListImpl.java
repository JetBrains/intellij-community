package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;

/**
 *  @author dsl
 */
public class PsiThrowsListImpl extends ReferenceListElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiThrowsListImpl");

  public PsiThrowsListImpl() {
    super(THROWS_LIST);
  }

  protected String getKeywordText() {
    return PsiKeyword.THROWS;
  }

  protected IElementType getKeywordType() {
    return THROWS_KEYWORD;
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.THROWS_KEYWORD:
        return TreeUtil.findChild(this, THROWS_KEYWORD);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == THROWS_KEYWORD) {
      return ChildRole.THROWS_KEYWORD;
    }
    else if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.REFERENCE_IN_LIST;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
