package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaFileElement extends FileElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.JavaFileElement");

  public JavaFileElement() {
    super(JAVA_FILE);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);
    if (before == null && first == last && first.getElementType() == ElementType.PACKAGE_STATEMENT){ //?
      anchor = getFirstChildNode();
      before = Boolean.TRUE;
    }
    return super.addInternal(first, last, anchor, before);
  }

  public void deleteChildInternal(@NotNull ASTNode child){
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

  @Nullable
  public ASTNode findChildByRole(int role) {
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

  public int getChildRole(ASTNode child) {
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
      return ChildRoleBase.NONE;
    }
  }

  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    if (newElement.getElementType() == ElementType.IMPORT_LIST) {
      LOG.assertTrue(child.getElementType() == ElementType.IMPORT_LIST);
      if (newElement.getFirstChildNode() == null) { //empty import list
        ASTNode next = child.getTreeNext();
        if (next != null && next.getElementType() == WHITE_SPACE) {
          removeChild(next);
        }
      }
    }
    super.replaceChildInternal(child, newElement);
  }
}
