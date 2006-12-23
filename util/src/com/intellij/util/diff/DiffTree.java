package com.intellij.util.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class DiffTree<OT, NT> {
  private static final int CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD = 20;

  private final DiffTreeStructure<OT> myOldTree;
  private final DiffTreeStructure<NT> myNewTree;
  private final ShallowNodeComparator<OT, NT> myComparator;
  private final DiffTreeChangeBuilder<OT, NT> myConsumer;
  private final List<List<OT>> myOldChildrenLists = new ArrayList<List<OT>>();
  private final List<List<NT>> myNewChildrenLists = new ArrayList<List<NT>>();

  public DiffTree(final DiffTreeStructure<OT> oldTree,
                  final DiffTreeStructure<NT> newTree,
                  final ShallowNodeComparator<OT, NT> comparator,
                  final DiffTreeChangeBuilder<OT, NT> consumer) {

    myOldTree = oldTree;
    myNewTree = newTree;
    myComparator = comparator;
    myConsumer = consumer;
  }

  public static <OT, NT> void diff(DiffTreeStructure<OT> oldTree, DiffTreeStructure<NT> newTree, ShallowNodeComparator<OT, NT> comparator, DiffTreeChangeBuilder<OT, NT> consumer) {
    new DiffTree<OT, NT>(oldTree, newTree, comparator, consumer).build(oldTree.getRoot(), newTree.getRoot(), 0);
  }

  private void build(OT oldN, NT newN, int level) {
    OT oldNode = myOldTree.prepareForGetChildren(oldN);
    NT newNode = myNewTree.prepareForGetChildren(newN);

    if (level >= myNewChildrenLists.size()) {
      myNewChildrenLists.add(new ArrayList<NT>());
      myOldChildrenLists.add(new ArrayList<OT>());
    }

    final List<OT> oldChildren = myOldChildrenLists.get(level);
    myOldTree.disposeChildren(oldChildren);
    oldChildren.clear();
    myOldTree.getChildren(oldNode, oldChildren);

    final List<NT> newChildren = myNewChildrenLists.get(level);
    myNewTree.disposeChildren(newChildren);
    newChildren.clear();
    myNewTree.getChildren(newNode, newChildren);

    final int oldSize = oldChildren.size();
    final int newSize = newChildren.size();

    if (Math.abs(oldSize - newSize) > CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD) {
      myConsumer.nodeReplaced(oldNode, newNode);
      return;
    }

    final ShallowNodeComparator<OT, NT> comparator = myComparator;
    if (oldSize == 0 && newSize == 0) {
      if (!comparator.hashcodesEqual(oldNode, newNode) || !comparator.typesEqual(oldNode, newNode)) {
        myConsumer.nodeReplaced(oldNode, newNode);
      }
      return;
    }

    boolean walkedDeep = false;

    ShallowNodeComparator.ThreeState[] deeps = oldSize == newSize ? new ShallowNodeComparator.ThreeState[oldSize] : null;

    int start = 0;
    while (start < oldSize && start < newSize) {
      OT oldChild = oldChildren.get(start);
      NT newChild = newChildren.get(start);
      if (!comparator.typesEqual(oldChild, newChild)) break;
      final ShallowNodeComparator.ThreeState dp = comparator.deepEqual(oldChild, newChild);
      if (deeps != null) deeps[start] = dp;

      if (dp != ShallowNodeComparator.ThreeState.YES) {
        if (!comparator.hashcodesEqual(oldChild, newChild)) break;
        build(oldChild, newChild, level + 1);
        walkedDeep = true;
      }

      start++;
    }

    int oldEnd = oldSize - 1;
    int newEnd = newSize - 1;

    if (oldSize == newSize && start == newSize) return; // No changes at all at this level

    while (oldEnd >= start && newEnd >= start) {
      OT oldChild = oldChildren.get(oldEnd);
      NT newChild = newChildren.get(newEnd);
      if (!comparator.typesEqual(oldChild, newChild)) break;
      final ShallowNodeComparator.ThreeState dp = comparator.deepEqual(oldChild, newChild);
      if (deeps != null) deeps[oldEnd] = dp;
      if (dp != ShallowNodeComparator.ThreeState.YES) {
        if (!comparator.hashcodesEqual(oldChild, newChild)) break;
        build(oldChild, newChild, level + 1);
        walkedDeep = true;
      }

      oldEnd--;
      newEnd--;
    }
    
    if (oldSize == newSize) {
      for (int i = start; i <= newEnd; i++) {
        final OT oldChild = oldChildren.get(i);
        final NT newChild = newChildren.get(i);

        if (comparator.typesEqual(oldChild, newChild)) {
          final ShallowNodeComparator.ThreeState de = deeps[i];
          if (de == ShallowNodeComparator.ThreeState.UNSURE) {
            build(oldChild, newChild, level + 1);
          }
          else if (de == ShallowNodeComparator.ThreeState.NO || de == null) {
            myConsumer.nodeReplaced(oldChild, newChild);
          }
        }
        else {
          myConsumer.nodeReplaced(oldChild, newChild);
        }
      }
    }
    else {
      if (!walkedDeep && start == 0 && newEnd == newSize - 1 && oldEnd == oldSize - 1 && start < oldEnd && start < newEnd) {
        myConsumer.nodeReplaced(oldNode, newNode);
        return;
      }

      for (int i = start; i <= oldEnd; i++) {
        final OT oldChild = oldChildren.get(i);
        myConsumer.nodeDeleted(oldNode, oldChild);
      }

      for (int i = start; i <= newEnd; i++) {
        myConsumer.nodeInserted(oldNode, newChildren.get(i), i);
      }
    }
  }
}
