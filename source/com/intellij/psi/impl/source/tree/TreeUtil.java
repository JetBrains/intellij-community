package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;

import java.util.HashSet;
import java.util.Set;

public class TreeUtil {
  public static TreeElement findChild(CompositeElement parent, IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(TreeElement element = parent.firstChild; element != null; element = element.getTreeNext()){
      if (element.getElementType() == type) return element;
    }
    return null;
  }

  public static TreeElement findChild(CompositeElement parent, TokenSet types) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(TreeElement element = parent.firstChild; element != null; element = element.getTreeNext()){
      if (types.isInSet(element.getElementType())) return element;
    }
    return null;
  }

  public static TreeElement findChildBackward(CompositeElement parent, IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(TreeElement element = parent.lastChild; element != null; element = element.getTreePrev()){
      if (element.getElementType() == type) return element;
    }
    return null;
  }

  public static TreeElement findChildBackward(CompositeElement parent, TokenSet types) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(TreeElement element = parent.lastChild; element != null; element = element.getTreePrev()){
      if (types.isInSet(element.getElementType())) return element;
    }
    return null;
  }

  public static TreeElement skipElements(TreeElement element, TokenSet types) {
    while(true){
      if (element == null) return null;
      if (!types.isInSet(element.getElementType())) break;
      element = element.getTreeNext();
    }
    return element;
  }

  public static TreeElement skipElementsBack(TreeElement element, TokenSet types) {
    while(true){
      if (element == null) return null;
      if (!types.isInSet(element.getElementType())) break;
      element = element.getTreePrev();
    }
    return element;
  }

  public static TreeElement findParent(TreeElement element, IElementType type) {
    for(TreeElement parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()){
      if (parent.getElementType() == type) return parent;
    }
    return null;
  }

  public static TreeElement findParent(TreeElement element, TokenSet types) {
    for(TreeElement parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()){
      if (types.isInSet(parent.getElementType())) return parent;
    }
    return null;
  }

  public static boolean isInRange(TreeElement element, TreeElement start, TreeElement end) {
    for(TreeElement child = start; child != end; child = child.getTreeNext()){
      if (child == element) return true;
    }
    return false;
  }

  public static LeafElement findFirstLeaf(TreeElement element) {
    if (element instanceof LeafElement){
      return (LeafElement)element;
    }
    else{
      for(TreeElement child = ((CompositeElement)element).firstChild; child != null; child = child.getTreeNext()){
        LeafElement leaf = findFirstLeaf(child);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  public static LeafElement findLastLeaf(TreeElement element) {
    if (element instanceof LeafElement){
      return (LeafElement)element;
    }
    else{
      for(TreeElement child = ((CompositeElement)element).lastChild; child != null; child = child.getTreePrev()){
        LeafElement leaf = findLastLeaf(child);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  public static void addChildren(CompositeElement parent, TreeElement first) {
    final TreeElement lastChild = parent.lastChild;
    if (lastChild == null){
      parent.firstChild = first;
      first.setTreePrev(null);
      while(true){
        final TreeElement treeNext = first.getTreeNext();
        if(first instanceof CompositeElement) first.setTreeParent(parent);
        if(treeNext == null) break;
        first = treeNext;
      }
      parent.lastChild = first;
      first.setTreeParent(parent);
    }
    else insertAfter(lastChild, first);
    if (DebugUtil.CHECK){
      DebugUtil.checkTreeStructure(parent);
    }
  }

  public static void insertBefore(TreeElement anchor, TreeElement firstNew) {
    final TreeElement anchorPrev = anchor.getTreePrev();
    if(anchorPrev == null){
      final CompositeElement parent = anchor.getTreeParent();
      if(parent != null) parent.firstChild = firstNew;
      while(true){
        final TreeElement treeNext = firstNew.getTreeNext();
        if(firstNew instanceof CompositeElement){
          firstNew.setTreeParent(parent);
        }
        if(treeNext == null) break;
        firstNew = treeNext;
      }
      anchor.setTreePrev(firstNew);
      firstNew.setTreeNext(anchor);
    }
    else insertAfter(anchorPrev, firstNew);

    if (DebugUtil.CHECK){
      DebugUtil.checkTreeStructure(anchor);
    }
  }

  public static void insertAfter(TreeElement anchor, TreeElement firstNew) {
    final CompositeElement parent = anchor.getTreeParent();
    final TreeElement treeNext = anchor.getTreeNext();
    firstNew.setTreePrev(anchor);
    anchor.setTreeNext(firstNew);
    while(true){
      final TreeElement next = firstNew.getTreeNext();
      if(firstNew instanceof CompositeElement){
        firstNew.setTreeParent(parent);
      }
      if(next == null) break;
      firstNew = next;
    }
    
    if(treeNext == null){
      if(parent != null){
        firstNew.setTreeParent(parent);
        parent.lastChild = firstNew;
      }
    }
    else{
      firstNew.setTreeNext(treeNext);
      treeNext.setTreePrev(firstNew);
    }
    if (DebugUtil.CHECK){
      DebugUtil.checkTreeStructure(anchor);
    }
  }

  public static void remove(TreeElement element) {
    final TreeElement next = element.getTreeNext();
    final CompositeElement parent = element.getTreeParent();
    final TreeElement prev = element.getTreePrev();
    if(prev != null){
      prev.setTreeNext(next);
    }
    else if(parent != null){
      parent.firstChild = next;
    }

    if(next != null){
      next.setTreePrev(prev);
    }
    else if(parent != null){
      parent.lastChild = prev;
    }

    if (DebugUtil.CHECK){
      if (element.getTreeParent() != null){
        DebugUtil.checkTreeStructure(element.getTreeParent());
      }
      if (element.getTreePrev() != null){
        DebugUtil.checkTreeStructure(element.getTreePrev());
      }
      if (element.getTreeNext() != null){
        DebugUtil.checkTreeStructure(element.getTreeNext());
      }
    }
    element.setTreePrev(null);
    element.setTreeParent(null);
    element.setTreeNext(null);
  }

  public static void removeRange(TreeElement start, TreeElement end) {
    if (start == null) return;
    if(start == end) return;
    final CompositeElement parent = start.getTreeParent();
    final TreeElement startPrev = start.getTreePrev();
    final TreeElement endPrev = end != null ? end.getTreePrev() : null;
    if (parent != null){
      if (start == parent.firstChild){
        parent.firstChild = end;
      }
      if (end == null){
        parent.lastChild = startPrev;
      }
    }
    if (startPrev != null){
      startPrev.setTreeNext(end);
    }
    if (end != null){
      end.setTreePrev(startPrev);
    }

    start.setTreePrev(null);
    if (endPrev != null){
      endPrev.setTreeNext(null);
    }
    if (parent != null){
      for(TreeElement element = start; element != null; element = element.getTreeNext()){
        if(element instanceof CompositeElement)
          element.setTreeParent(null);
      }
    }

    if (DebugUtil.CHECK){
      if (parent != null){
        DebugUtil.checkTreeStructure(parent);
      }
      DebugUtil.checkTreeStructure(start);
    }
  }

  public static void replace(TreeElement old, TreeElement firstNew) {
    if (firstNew != null){
      insertAfter(old, firstNew);
    }
    remove(old);
  }

  public static TreeElement findSibling(TreeElement start, IElementType elementType) {
    TreeElement child = start;
    while (true) {
      if (child == null) return null;
      if (child.getElementType() == elementType) return child;
      child = child.getTreeNext();
    }
  }

  public static TreeElement findSibling(TreeElement start, TokenSet types) {
    TreeElement child = start;
    while (true) {
      if (child == null) return null;
      if (types.isInSet(child.getElementType())) return child;
      child = child.getTreeNext();
    }
  }

  public static TreeElement findCommonParent(TreeElement one, TreeElement two){
    // optimization
    if(one == two) return one;
    final Set<TreeElement> parents = new HashSet<TreeElement>(20);
    do{
      parents.add(one);
    } while((one = one.getTreeParent()) != null);

    while((two = two.getTreeParent()) != null){
      if(parents.contains(two)) return two;
    }
    return null;
  }

  public static int getNotCachedLength(TreeElement tree, CharTable table) {
    int length = 0;

    if (tree instanceof LeafElement) {
      length += tree.getTextLength(table);
    }
    else{
      final CompositeElement composite = (CompositeElement)tree;
      TreeElement firstChild = composite.firstChild;
      while(firstChild != null){
        length += getNotCachedLength(firstChild, table);
        firstChild = firstChild.getTreeNext();
      }
    }
    return length;
  }

  public static int countLeafs(CompositeElement composite) {
    int count = 0;
    TreeElement child = composite.firstChild;
    while(child != null){
      if(child instanceof LeafElement) count++;
      else count += countLeafs((CompositeElement)child);
      child = child.getTreeNext();
    }
    return count;
  }

  public static void clearCaches(TreeElement tree) {
    tree.clearCaches();
    if(tree instanceof CompositeElement){
      final CompositeElement composite = ((CompositeElement)tree);
      TreeElement child = composite.firstChild;
      while(child != null){
        clearCaches(child);
        child = child.getTreeNext();
      }
    }
  }
}
