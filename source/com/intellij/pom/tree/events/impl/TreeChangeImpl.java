package com.intellij.pom.tree.events.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.ReplaceChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;

import java.util.*;

public class TreeChangeImpl implements TreeChange {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.events.impl.TreeChangeImpl");
  private final Map<ASTNode, ChangeInfo> myChanges = new HashMap<ASTNode, ChangeInfo>();
  private final List<Pair<ASTNode,Integer>> myOffsets = new ArrayList<Pair<ASTNode, Integer>>();
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
        addChangeInternal(child, changeInfo);
      }
      else{
        switch(replacedInfo.getChangeType()){
          case ChangeInfo.REPLACE:
            replaceChangeInfo.setOldLength(replacedInfo.getOldLength());
            replaceChangeInfo.setReplaced(((ReplaceChangeInfo)replacedInfo).getReplaced());
            break;
          case ChangeInfo.ADD:
            changeInfo = ChangeInfoImpl.create(ChangeInfo.ADD, replaced, SharedImplUtil.findCharTableByTree(myParent));
            removeChangeInternal(child);
            break;
        }
        addChangeInternal(child, changeInfo);
      }
      return;
    }

    if(current != null && current.getChangeType() == ChangeInfo.REMOVED){
      if(changeInfo.getChangeType() == ChangeInfo.ADD){
        removeChangeInternal(child);
      }
      return;
    }

    if(current != null && current.getChangeType() == ChangeInfo.ADD){
      if(changeInfo.getChangeType() == ChangeInfo.REMOVED){
        removeChangeInternal(child);
      }
      return;
    }

    if(changeInfo.getChangeType() == ChangeInfo.REMOVED){
      if(child instanceof LeafElement){
        final CharSequence charTabIndex = ((LeafElement)child).getInternedText();
        if(checkLeaf(child.getTreeNext(), charTabIndex) || checkLeaf(child.getTreePrev(), charTabIndex)) return;
      }
      addChangeInternal(child, changeInfo);
      if (current != null) {
        ((ChangeInfoImpl)changeInfo).setOldLength(current.getOldLength());
      }
      return;
    }

    if(current == null){
      addChangeInternal(child, changeInfo);
      return;
    }
  }

  private void addChangeInternal(ASTNode child, ChangeInfo info){
    myChanges.put(child, info);
    final int nodeOffset = getNodeOffset(child);
    final int n = myOffsets.size();
    for(int i = 0; i < n; i++){
      final Pair<ASTNode, Integer> pair = myOffsets.get(i);
      if(child == pair.getFirst()) return;
      if(nodeOffset < pair.getSecond().intValue()){
        myOffsets.add(i, new Pair<ASTNode, Integer>(child, new Integer(nodeOffset)));
        return;
      }
    }
    myOffsets.add(new Pair<ASTNode, Integer>(child, new Integer(nodeOffset)));
  }

  private void removeChangeInternal(ASTNode child){
    myChanges.remove(child);
    final int n = myOffsets.size();
    for(int i = 0; i < n; i++){
      if(child ==  myOffsets.get(i).getFirst()){
        myOffsets.remove(i);
        break;
      }
    }
  }


  private boolean checkLeaf(final ASTNode treeNext, final CharSequence charTabIndex) {
    if(!(treeNext instanceof LeafElement)) return false;
    final ChangeInfo right = myChanges.get(treeNext);
    if(right != null && right.getChangeType() == ChangeInfo.ADD){
      if(charTabIndex == ((LeafElement)treeNext).getInternedText()){
        removeChangeInternal(treeNext);
        return true;
      }
    }
    return false;
  }

  public TreeElement[] getAffectedChildren() {
    final TreeElement[] treeElements = new TreeElement[myChanges.size()];
    int index = 0;
    final Iterator<Pair<ASTNode, Integer>> iterator = myOffsets.iterator();
    while(iterator.hasNext()){
      final Pair<ASTNode, Integer> pair = iterator.next();
      treeElements[index++] = (TreeElement)pair.getFirst();
    }
    return treeElements;
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
    removeChangeInternal(beforeEqualDepth);
  }

  private int getNodeOffset(ASTNode child){
    LOG.assertTrue(child.getTreeParent() == myParent);
    final CharTable table = SharedImplUtil.findCharTableByTree(myParent);
    ASTNode current = myParent.getFirstChildNode();
    final Iterator<Pair<ASTNode, Integer>> iterator = myOffsets.iterator();
    Pair<ASTNode, Integer> currentChange = iterator.hasNext() ? iterator.next() : null;
    int currentOffset = 0;
    do{
      boolean counted = false;
      while(currentChange != null && currentOffset == currentChange.getSecond().intValue()){
        if(current == currentChange.getFirst()) counted = true;
        final ChangeInfo changeInfo = myChanges.get(currentChange.getFirst());
        currentOffset += changeInfo.getOldLength();
        currentChange = iterator.hasNext() ? iterator.next() : null;
      }
      if(current == child) break;
      if(!counted) currentOffset += ((TreeElement)current).getTextLength(table);
      current = current.getTreeNext();
    }
    while(true);
    return currentOffset;
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
