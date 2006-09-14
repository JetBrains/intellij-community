package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.CharTable;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.JavaTokenType;
import com.intellij.lang.ASTNode;

public class MethodElement extends RepositoryTreeElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.MethodElement");

  public MethodElement() {
    super(METHOD);
  }

  protected MethodElement(IElementType type) {
    super(type);
  }

  public int getTextOffset() {
    return findChildByRole(ChildRole.NAME).getStartOffset();
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getElementType() == ElementType.CODE_BLOCK){
      ASTNode semicolon = findChildByRole(ChildRole.CLOSING_SEMICOLON);
      if (semicolon != null){
        deleteChildInternal(semicolon);
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  public void deleteChildInternal(ASTNode child) {
    super.deleteChildInternal(child);
    if (child.getElementType() == CODE_BLOCK){
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      LeafElement semicolon = Factory.createSingleLeafElement(SEMICOLON, new char[]{';'}, 0, 1, treeCharTab, getManager());
      this.addInternal(semicolon, semicolon, null, Boolean.TRUE);
    }
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.DOC_COMMENT:
        return PsiImplUtil.findDocComment(this);

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, MODIFIER_LIST);

      case ChildRole.TYPE_PARAMETER_LIST:
        return TreeUtil.findChild(this, TYPE_PARAMETER_LIST);

      case ChildRole.NAME:
        return TreeUtil.findChild(this, IDENTIFIER);

      case ChildRole.TYPE:
        return TreeUtil.findChild(this, TYPE);

      case ChildRole.METHOD_BODY:
        return TreeUtil.findChild(this, CODE_BLOCK);

      case ChildRole.PARAMETER_LIST:
        return TreeUtil.findChild(this, PARAMETER_LIST);

      case ChildRole.THROWS_LIST:
        return TreeUtil.findChild(this, THROWS_LIST);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.DOC_COMMENT || i == JavaDocElementType.DOC_COMMENT) {
      return getChildRole(child, ChildRole.DOC_COMMENT);
    }
    else if (i == C_STYLE_COMMENT || i == END_OF_LINE_COMMENT) {
      {
        return ChildRole.NONE;
      }
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == TYPE_PARAMETER_LIST) {
      return ChildRole.TYPE_PARAMETER_LIST;
    }
    else if (i == CODE_BLOCK) {
      return ChildRole.METHOD_BODY;
    }
    else if (i == PARAMETER_LIST) {
      return ChildRole.PARAMETER_LIST;
    }
    else if (i == THROWS_LIST) {
      return ChildRole.THROWS_LIST;
    }
    else if (i == TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == SEMICOLON) {
      return getChildRole(child, ChildRole.CLOSING_SEMICOLON);
    }
    else {
      return ChildRole.NONE;
    }
  }
}
