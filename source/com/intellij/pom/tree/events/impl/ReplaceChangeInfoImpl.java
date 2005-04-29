package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.CharTable;
import com.intellij.pom.tree.events.ReplaceChangeInfo;

public class ReplaceChangeInfoImpl extends ChangeInfoImpl implements ReplaceChangeInfo {
  private ASTNode myReplaced;
  private ASTNode myChanged;

  public ReplaceChangeInfoImpl(ASTNode changed) {
    super(REPLACE, changed);
    myChanged = changed;
  }

  public ASTNode getReplaced(){
    return myReplaced;
  }

  public void setReplaced(ASTNode replaced) {
    CharTable charTableByTree = SharedImplUtil.findCharTableByTree(myChanged);
    setOldLength(TreeUtil.getNotCachedLength(replaced));
    myReplaced = replaced;
    myReplaced.putUserData(CharTable.CHAR_TABLE_KEY, charTableByTree);
  }
}
