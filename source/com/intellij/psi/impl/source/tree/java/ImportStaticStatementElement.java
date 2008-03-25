package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.lang.ASTNode;

public class ImportStaticStatementElement extends ImportStatementBaseElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ImportStatementElement");

  public ImportStaticStatementElement() {
    super(IMPORT_STATIC_STATEMENT);
  }

  public ASTNode findChildByRole(int role) {
    final ASTNode result = super.findChildByRole(role);
    if (result != null) return result;
    switch (role) {
      default:
        return null;

      case ChildRole.IMPORT_REFERENCE:
        final ASTNode importStaticReference = TreeUtil.findChild(this, IMPORT_STATIC_REFERENCE);
        if (importStaticReference != null) {
          return importStaticReference;
        }
        else {
          return TreeUtil.findChild(this, JAVA_CODE_REFERENCE);
        }
    }
  }

  public int getChildRole(ASTNode child) {
    final int role = super.getChildRole(child);
    if (role != ChildRoleBase.NONE) return role;
    if (child.getElementType() == IMPORT_STATIC_REFERENCE) {
      return ChildRole.IMPORT_REFERENCE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
