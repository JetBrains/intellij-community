package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.IncorrectOperationException;

public class JavaFileElement extends FileElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.JavaFileElement");

  public JavaFileElement() {
    super(JAVA_FILE);
  }

  public TreeElement addInternal(TreeElement first, TreeElement last, TreeElement anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);
    if (before == null && first == last && first.getElementType() == ElementType.PACKAGE_STATEMENT){ //?
      anchor = firstChild;
      before = Boolean.TRUE;
    }
    return super.addInternal(first, last, anchor, before);
  }

  public void deleteChildInternal(TreeElement child){
    if (child.getElementType() == CLASS){
      PsiJavaFile file = (PsiJavaFile)SourceTreeToPsiMap.treeElementToPsi(this);
      if (file.getClasses().length == 1){
        try{
          file.delete();
        }
        catch(IncorrectOperationException e){
          LOG.error(e);
        }
        return;
      }
    }
    super.deleteChildInternal(child);
  }

  public TreeElement findChildByRole(int role) {
    ChameleonTransforming.transformChildren(this);
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.PACKAGE_STATEMENT:
        return TreeUtil.findChild(this, PACKAGE_STATEMENT);

      case ChildRole.IMPORT_LIST:
        return TreeUtil.findChild(this, IMPORT_LIST);
    }
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == PACKAGE_STATEMENT) {
      return ChildRole.PACKAGE_STATEMENT;
    }
    else if (i == IMPORT_LIST) {
      return ChildRole.IMPORT_LIST;
    }
    else if (i == CLASS) {
      return ChildRole.CLASS;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
