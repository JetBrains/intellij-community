package com.intellij.pom.tree.events.impl;

import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.ReplaceChangeInfo;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.ASTNode;

import java.util.*;

public class TreeChangeImpl implements TreeChange {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.events.impl.TreeChangeImpl");
  private final Map<ASTNode, ChangeInfo> myChanges = new LinkedHashMap<ASTNode, ChangeInfo>();
  private final ASTNode myParent;

  public TreeChangeImpl(ASTNode parent) {
    myParent = parent;
  }

  public void addChange(ASTNode child, ChangeInfo changeInfo) {
    LOG.assertTrue(child.getTreeParent() == myParent);

    final ChangeInfo current = myChanges.get(child);

    if(current != null && changeInfo.getChangeType() == ChangeInfo.CONTENTS_CHANGED){
      return;
    }

    if(changeInfo.getChangeType() == ChangeInfo.REPLACE){
      final ReplaceChangeInfoImpl replaceChangeInfo = ((ReplaceChangeInfoImpl)changeInfo);
      final ASTNode replaced = replaceChangeInfo.getReplaced();
      final ChangeInfo replacedInfo = myChanges.get(replaced);

      if(replacedInfo == null){
        //myChanges.put(replaced, ChangeInfo.create(ChangeInfo.REMOVED, replaced, SharedImplUtil.findCharTableByTree(myParent)));
        myChanges.put(child, changeInfo);
      }
      else{
        switch(replacedInfo.getChangeType()){
          case ChangeInfo.REPLACE:
            replaceChangeInfo.setOldLength(replacedInfo.getOldLength());
            replaceChangeInfo.setReplaced(((ReplaceChangeInfo)replacedInfo).getReplaced());
            break;
          case ChangeInfo.ADD:
            changeInfo = ChangeInfoImpl.create(ChangeInfo.ADD, replaced, SharedImplUtil.findCharTableByTree(myParent));
            myChanges.remove(replaced);
            break;
        }
        myChanges.put(child, changeInfo);
      }
      return;
    }

    if(current != null && current.getChangeType() == ChangeInfo.REMOVED){
      if(changeInfo.getChangeType() == ChangeInfo.ADD){
        myChanges.remove(child);
      }
      return;
    }

    if(current != null && current.getChangeType() == ChangeInfo.ADD){
      if(changeInfo.getChangeType() == ChangeInfo.REMOVED){
        myChanges.remove(child);
      }
      return;
    }

    if(changeInfo.getChangeType() == ChangeInfo.REMOVED){
      if(child instanceof LeafElement){
        final int charTabIndex = ((LeafElement)child).getCharTabIndex();
        if(checkLeaf(child.getTreeNext(), charTabIndex) || checkLeaf(child.getTreePrev(), charTabIndex)) return;
      }
      myChanges.put(child, changeInfo);
      if(current != null)
        ((ChangeInfoImpl)changeInfo).setOldLength(current.getOldLength());
      return;
    }

    if(current == null){
      myChanges.put(child, changeInfo);
      return;
    }
  }

  private boolean checkLeaf(final ASTNode treeNext, final int charTabIndex) {
    if(!(treeNext instanceof LeafElement)) return false;
    final ChangeInfo right = myChanges.get(treeNext);
    if(right != null && right.getChangeType() == ChangeInfo.ADD){
      if(charTabIndex == ((LeafElement)treeNext).getCharTabIndex()){
        myChanges.remove(treeNext);
        return true;
      }
    }
    return false;
  }

  public TreeElement[] getAffectedChildren() {
    return myChanges.keySet().toArray(new TreeElement[myChanges.size()]);
  }

  public ChangeInfo getChangeByChild(ASTNode child) {
    return myChanges.get(child);
  }

  public void composite(TreeChange treeChange) {
    final TreeChangeImpl change = (TreeChangeImpl)treeChange;
    final Set<Map.Entry<ASTNode,ChangeInfo>> entries = change.myChanges.entrySet();
    final Iterator<Map.Entry<ASTNode, ChangeInfo>> iterator = entries.iterator();
    while (iterator.hasNext()) {
      final Map.Entry<ASTNode, ChangeInfo> entry = iterator.next();
      addChange(entry.getKey(), entry.getValue());
    }
  }

  public boolean isEmpty() {
    return false;
  }

  public void removeChange(ASTNode beforeEqualDepth) {
    myChanges.remove(beforeEqualDepth);
  }

  public String toString(){
    final StringBuffer buffer = new StringBuffer();
    final Iterator<Map.Entry<ASTNode, ChangeInfo>> iterator = myChanges.entrySet().iterator();
    while (iterator.hasNext()) {
      final Map.Entry<ASTNode, ChangeInfo> entry = iterator.next();
      buffer.append("(");
      buffer.append(entry.getKey().getElementType().toString());
      buffer.append(", ");
      buffer.append(entry.getValue().toString());
      buffer.append(")");
      if(iterator.hasNext()) buffer.append(", ");
    }
    return buffer.toString();
  }
}
