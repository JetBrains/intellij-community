package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.CharTable;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.TreeChange;

public class ChangeInfoImpl implements ChangeInfo {
  private static final String[] TO_STRING = {"add", "remove", "replace", "changed"};

  public static ChangeInfoImpl create(short type, ASTNode changed, CharTable table){
    switch(type){
      case REPLACE:
        return new ReplaceChangeInfoImpl(changed, table);
      default:
        return new ChangeInfoImpl(type, changed, table);
    }
  }

  private final short type;
  private int myOldLength = 0;

  protected ChangeInfoImpl(short type, ASTNode changed, CharTable table){
    this.type = type;
    myOldLength = TreeUtil.getNotCachedLength(changed, table);
  }

  public int getChangeType(){
    return type;
  }

  public String toString(){
    return TO_STRING[getChangeType()];
  }

  public void compactChange(ASTNode parent, TreeChange change){
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(parent);
    final ASTNode[] affectedChildren = change.getAffectedChildren();
    for (int i = 0; i < affectedChildren.length; i++) {
      final ASTNode treeElement = affectedChildren[i];
      final ChangeInfo changeByChild = change.getChangeByChild(treeElement);
      processElementaryChange(changeByChild, treeElement, charTableByTree);
    }
  }

  public void processElementaryChange(ASTNode parent, final ChangeInfo changeByChild, final ASTNode treeElement) {
    processElementaryChange(changeByChild, treeElement, SharedImplUtil.findCharTableByTree(parent));
  }

  private void processElementaryChange(final ChangeInfo changeByChild, final ASTNode treeElement, final CharTable charTableByTree) {
    switch(changeByChild.getChangeType()){
      case ADD:
        myOldLength -= TreeUtil.getNotCachedLength(treeElement, charTableByTree);
        break;
      case REMOVED:
        myOldLength += changeByChild.getOldLength();
        break;
      case REPLACE:
        myOldLength -= TreeUtil.getNotCachedLength(treeElement, charTableByTree);
        myOldLength += changeByChild.getOldLength();
        break;
      case CONTENTS_CHANGED:
        myOldLength -= TreeUtil.getNotCachedLength(treeElement, charTableByTree);
        myOldLength += changeByChild.getOldLength();
        break;
    }
  }

  public int getOldLength(){
    return myOldLength;
  }

  public void setOldLength(int oldTreeLength) {
    myOldLength = oldTreeLength;
  }

}
