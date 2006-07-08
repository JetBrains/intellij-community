package com.intellij.util.diff;

import java.util.List;

/**
 * @author max
 */
public class DiffTree<OT, NT> {
  private final DiffTreeStructure<OT> myOldTree;
  private final DiffTreeStructure<NT> myNewTree;
  private final ShallowNodeComparator<OT, NT> myComparator;
  private final DiffTreeChangeBuilder<OT, NT> myConsumer;

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
    new DiffTree<OT, NT>(oldTree, newTree, comparator, consumer).build(oldTree.getRoot(), newTree.getRoot());
  }

  private void build(OT oldNode, NT newNode) {
    oldNode = myOldTree.prepareForGetChildren(oldNode);
    newNode = myNewTree.prepareForGetChildren(newNode);

    final List<OT> oldChildren = myOldTree.getChildren(oldNode);
    final List<NT> newChildren = myNewTree.getChildren(newNode);

    final int oldSize = oldChildren.size();
    final int newSize = newChildren.size();

    if (oldSize == 0 && newSize == 0) {
      if (!myComparator.hashcodesEqual(oldNode, newNode) || !myComparator.typesEqual(oldNode, newNode)) {
        myConsumer.nodeReplaced(oldNode, newNode);
      }
      return;
    }

    int start = 0;
    while (start < oldSize && start < newSize) {
      OT oldChild = oldChildren.get(start);
      NT newChild = newChildren.get(start);
      if (!myComparator.typesEqual(oldChild, newChild) || !myComparator.hashcodesEqual(oldChild, newChild)) break;
      if (myComparator.deepEqual(oldChild, newChild) != ShallowNodeComparator.ThreeState.YES) {
        build(oldChild, newChild);
      }

      start++;
    }

    int oldEnd = oldSize - 1;
    int newEnd = newSize - 1;

    if (oldSize == newSize && start == newSize) return; // No changes at all at this level

    while (oldEnd >= start && newEnd >= start) {
      OT oldChild = oldChildren.get(oldEnd);
      NT newChild = newChildren.get(newEnd);
      if (!myComparator.typesEqual(oldChild, newChild) || !myComparator.hashcodesEqual(oldChild, newChild)) break;
      if (myComparator.deepEqual(oldChild, newChild) != ShallowNodeComparator.ThreeState.YES) {
        build(oldChild, newChild);
      }

      oldEnd--;
      newEnd--;
    }

    if (oldSize == newSize) {
      for (int i = start; i <= newEnd; i++) {
        final OT oldChild = oldChildren.get(i);
        final NT newChild = newChildren.get(i);

        if (myComparator.typesEqual(oldChild, newChild)) {
          build(oldChild, newChild);
        }
        else {
          myConsumer.nodeReplaced(oldChild, newChild);
        }
      }
    }
    else {
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
