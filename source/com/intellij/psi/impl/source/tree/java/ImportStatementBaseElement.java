package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public class ImportStatementBaseElement extends RepositoryTreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ImportStatementBaseElement");
  public ImportStatementBaseElement(IElementType type) {
    super(type);
  }

  public TreeElement findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.IMPORT_KEYWORD:
        return firstChild;

      case ChildRole.IMPORT_ON_DEMAND_DOT:
        return TreeUtil.findChild(this, DOT);

      case ChildRole.IMPORT_ON_DEMAND_ASTERISK:
        return TreeUtil.findChild(this, ASTERISK);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, SEMICOLON);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == IMPORT_KEYWORD) {
      return ChildRole.IMPORT_KEYWORD;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.IMPORT_REFERENCE;
    }
    else if (i == DOT) {
      return ChildRole.IMPORT_ON_DEMAND_DOT;
    }
    else if (i == ASTERISK) {
      return ChildRole.IMPORT_ON_DEMAND_ASTERISK;
    }
    else if (i == SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
