package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public class SourceUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SourceUtil");

  private SourceUtil() {
  }

  public static String getTextSkipWhiteSpaceAndComments(ASTNode element) {
    int length = AstBufferUtil.toBuffer(element, null, 0, StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
    char[] buffer = new char[length];
    AstBufferUtil.toBuffer(element, buffer, 0, StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
    return new String(buffer);
  }

  public static TreeElement addParenthToReplacedChild(final IElementType parenthType,
                                                      TreeElement newChild,
                                                      PsiManager manager) {
    CompositeElement parenthExpr = ASTFactory.composite(parenthType);

    TreeElement dummyExpr = (TreeElement)newChild.clone();
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(newChild);
    new DummyHolder(manager, parenthExpr, null, charTableByTree);
    parenthExpr.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
    TreeUtil.addChildren(parenthExpr, ASTFactory.leaf(JavaTokenType.LPARENTH, "(", 0, 1, charTableByTree));
    TreeUtil.addChildren(parenthExpr, dummyExpr);
    TreeUtil.addChildren(parenthExpr, ASTFactory.leaf(JavaTokenType.RPARENTH, ")", 0, 1, charTableByTree));

    try {
      CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
      parenthExpr =
      (CompositeElement)SourceTreeToPsiMap.psiElementToTree(
        codeStyleManager.reformat(SourceTreeToPsiMap.treeElementToPsi(parenthExpr)));
    }
    catch (IncorrectOperationException e) {
      LOG.error(e); // should not happen
    }

    newChild.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(newChild));
    TreeUtil.replaceWithList(dummyExpr, newChild);

    newChild = parenthExpr;
    // TODO remove explicit caches drop since this should be ok iff we will use ChangeUtil for the modification 
    TreeUtil.clearCaches(TreeUtil.getFileElement(newChild));
    return newChild;
  }
}
