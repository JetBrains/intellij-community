package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;

public class ImportStaticStatementElement extends ImportStatementBaseElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ImportStatementElement");

  public ImportStaticStatementElement() {
    super(IMPORT_STATIC_STATEMENT);
  }

  public TreeElement findChildByRole(int role) {
    final TreeElement result = super.findChildByRole(role);
    if (result != null) return result;
    switch (role) {
      default:
        return null;

      case ChildRole.IMPORT_REFERENCE:
        final TreeElement importStaticReference = TreeUtil.findChild(this, IMPORT_STATIC_REFERENCE);
        if (importStaticReference != null) {
          return importStaticReference;
        }
        else {
          return TreeUtil.findChild(this, JAVA_CODE_REFERENCE);
        }
    }
  }

  public int getChildRole(TreeElement child) {
    final int role = super.getChildRole(child);
    if (role != ChildRole.NONE) return role;
    if (child.getElementType() == IMPORT_STATIC_REFERENCE) {
      return ChildRole.IMPORT_REFERENCE;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
