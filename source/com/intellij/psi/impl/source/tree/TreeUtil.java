package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.parsing.ParseUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeUtil {

  public static ASTNode findChild(ASTNode parent, IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(ASTNode element = parent.getFirstChildNode(); element != null; element = element.getTreeNext()){
      if (element.getElementType() == type) return element;
    }
    return null;
  }

  public static ASTNode findChild(ASTNode parent, TokenSet types) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(ASTNode element = parent.getFirstChildNode(); element != null; element = element.getTreeNext()){
      if (types.contains(element.getElementType())) return element;
    }
    return null;
  }

  public static ASTNode[] findChildren(ASTNode parent, TokenSet types) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    List<ASTNode> result = new ArrayList<ASTNode>();
    for(ASTNode element = parent.getFirstChildNode(); element != null; element = element.getTreeNext()){
      if (types.contains(element.getElementType())) {
        result.add(element);
      }
    }
    return result.toArray(new ASTNode[result.size()]);
  }

  public static ASTNode findChildBackward(ASTNode parent, IElementType type) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(ASTNode element = parent.getLastChildNode(); element != null; element = element.getTreePrev()){
      if (element.getElementType() == type) return element;
    }
    return null;
  }

  public static ASTNode findChildBackward(ASTNode parent, TokenSet types) {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED){
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    for(ASTNode element = parent.getLastChildNode(); element != null; element = element.getTreePrev()){
      if (types.contains(element.getElementType())) return element;
    }
    return null;
  }

  public static ASTNode skipElements(ASTNode element, TokenSet types) {
    while(true){
      if (element == null) return null;
      if (!types.contains(element.getElementType())) break;
      element = element.getTreeNext();
    }
    return element;
  }

  public static ASTNode skipElementsBack(ASTNode element, TokenSet types) {
    if (!types.contains(element.getElementType())) return element;

    ASTNode parent = element.getTreeParent();
    ASTNode prev = element;
    while (prev instanceof CompositeElement) {
      if (!types.contains(prev.getElementType())) return prev;
      prev = prev.getTreePrev();
    }
    if (prev == null) return null;
    ASTNode firstChildNode = parent.getFirstChildNode();
    ASTNode lastRelevant = null;
    while(firstChildNode != prev){
      if (!types.contains(firstChildNode.getElementType())) lastRelevant = firstChildNode;
      firstChildNode = firstChildNode.getTreeNext();
    }
    return lastRelevant;
  }

  public static ASTNode findParent(ASTNode element, IElementType type) {
    for(ASTNode parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()){
      if (parent.getElementType() == type) return parent;
    }
    return null;
  }

  public static ASTNode findParent(ASTNode element, TokenSet types) {
    for(ASTNode parent = element.getTreeParent(); parent != null; parent = parent.getTreeParent()){
      if (types.contains(parent.getElementType())) return parent;
    }
    return null;
  }

  public static boolean isInRange(ASTNode element, ASTNode start, ASTNode end) {
    for(ASTNode child = start; child != end; child = child.getTreeNext()){
      if (child == element) return true;
    }
    return false;
  }

  public static LeafElement findFirstLeaf(ASTNode element) {
    if (element instanceof LeafElement){
      return (LeafElement)element;
    }
    else{
      for(ASTNode child = element.getFirstChildNode(); child != null; child = child.getTreeNext()){
        LeafElement leaf = findFirstLeaf(child);
        if (leaf != null) return leaf;
      }
      return null;
    }
  }

  public static LeafElement findLastLeaf(ASTNode element) {
    if (element instanceof LeafElement){
      return (LeafElement)element;
    }
    else{
      for(ASTNode child = element.getLastChildNode(); child != null; child = child.getTreePrev()){
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
        first.setTreeParent(parent);
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

  public static void insertBefore(final TreeElement anchor, TreeElement firstNew) {
    final TreeElement anchorPrev = anchor.getTreePrev();
    if(anchorPrev == null){
      removeRange(firstNew, null);
      final CompositeElement parent = anchor.getTreeParent();
      if(parent != null) parent.firstChild = firstNew;
      while(true){
        final TreeElement treeNext = firstNew.getTreeNext();
        assert treeNext != anchor : "Attempt to create cycle";
        firstNew.setTreeParent(parent);
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

  public static void insertAfter(final TreeElement anchor, TreeElement firstNew) {
    removeRange(firstNew, null);
    final CompositeElement parent = anchor.getTreeParent();
    final TreeElement treeNext = anchor.getTreeNext();
    firstNew.setTreePrev(anchor);
    anchor.setTreeNext(firstNew);
    while(true){
      final TreeElement next = firstNew.getTreeNext();
      assert next != anchor : "Attempt to create cycle";
      firstNew.setTreeParent(parent);
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
    invalidate(element);
  }

  // remove nodes from start[including] to end[excluding] from the parent
  public static void removeRange(TreeElement start, TreeElement end) {
    if (start == null) return;
    if(start == end) return;
    final CompositeElement parent = start.getTreeParent();
    final TreeElement startPrev = start.getTreePrev();
    final TreeElement endPrev = end != null ? end.getTreePrev() : null;

    assert end == null || end.getTreeParent() == parent : "Trying to remove non-child";

    if (parent != null){
      if (start == parent.getFirstChildNode()){
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

  public static void replaceWithList(TreeElement old, TreeElement firstNew) {
    if (firstNew != null){
      insertAfter(old, firstNew);
    }
    remove(old);
  }

  public static ASTNode findSibling(ASTNode start, IElementType elementType) {
    ASTNode child = start;
    while (true) {
      if (child == null) return null;
      if (child.getElementType() == elementType) return child;
      child = child.getTreeNext();
    }
  }

  public static ASTNode findSibling(ASTNode start, TokenSet types) {
    ASTNode child = start;
    while (true) {
      if (child == null) return null;
      if (types.contains(child.getElementType())) return child;
      child = child.getTreeNext();
    }
  }

  public static ASTNode findCommonParent(ASTNode one, ASTNode two){
    // optimization
    if(one == two) return one;
    final Set<ASTNode> parents = new HashSet<ASTNode>(20);
    do{
      parents.add(one);
    } while((one = one.getTreeParent()) != null);

    while(two != null){
      if(parents.contains(two)) return two;
      two = two.getTreeParent();
    }
    return null;
  }

  public static int getNotCachedLength(ASTNode tree) {
    int length = 0;

    if (tree instanceof LeafElement) {
      length += tree.getTextLength();
    }
    else{
      final ASTNode composite = tree;
      TreeElement firstChild = (TreeElement)composite.getFirstChildNode();
      while(firstChild != null){
        length += getNotCachedLength(firstChild);
        firstChild = firstChild.getTreeNext();
      }
    }
    return length;
  }

  public static int countLeafs(ASTNode composite) {
    int count = 0;
    ASTNode child = composite.getFirstChildNode();
    while(child != null){
      if(child instanceof LeafElement) count++;
      else count += countLeafs(child);
      child = child.getTreeNext();
    }
    return count;
  }

  public static void clearCaches(TreeElement tree) {
    tree.clearCaches();
    if(tree instanceof CompositeElement){
      final ASTNode composite = tree;
      TreeElement child = (TreeElement)composite.getFirstChildNode();
      while(child != null){
        clearCaches(child);
        child = child.getTreeNext();
      }
    }
  }

  public static ASTNode nextLeaf(final ASTNode node) {
    return ParseUtil.nextLeaf((TreeElement)node, null);
  }

  public static FileElement getFileElement(TreeElement parent) {
    while(parent != null && !(parent instanceof FileElement)) {
      parent = parent.getTreeParent();
    }
    return (FileElement)parent;
  }

  public static void invalidate(final TreeElement element) {
    // invalidate replaced element
    element.setTreeNext(null);
    element.setTreePrev(null);
    element.setTreeParent(null);
  }

  public static ASTNode prevLeaf(final ASTNode node) {
    return ParseUtil.prevLeaf((TreeElement)node, null);
  }

  public static boolean containsErrors(final TreeElement treeNext) {
    if(treeNext.getElementType() == ElementType.ERROR_ELEMENT) return true;
    if(treeNext instanceof CompositeElement){
      final CompositeElement composite = (CompositeElement)treeNext;
      ASTNode firstChildNode = composite.getFirstChildNode();
      while(firstChildNode != null){
        if(containsErrors((TreeElement)firstChildNode)) return true;
        firstChildNode = firstChildNode.getTreeNext();
      }
      return false;
    }
    else return false;
  }
  public static int getOffsetInParent(final ASTNode child) {
    int childOffsetInParent = 0;
    ASTNode current = child.getTreeParent().getFirstChildNode();
    while(current != child) {
      childOffsetInParent += current.getTextLength();
      current = current.getTreeNext();
    }
    return childOffsetInParent;
  }
}
