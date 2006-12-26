package com.intellij.util.diff;

import com.intellij.openapi.util.Ref;

import java.util.ArrayList;
import java.util.Arrays;
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
  private final List<Ref<OT[]>> myOldChildrenLists = new ArrayList<Ref<OT[]>>();
  private final List<Ref<NT[]>> myNewChildrenLists = new ArrayList<Ref<NT[]>>();
  private final List<ShallowNodeComparator.ThreeState[]> myDeepStates = new ArrayList<ShallowNodeComparator.ThreeState[]>();

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

  // TODO: disposeChildren
  private void build(OT oldN, NT newN, int level) {
    OT oldNode = myOldTree.prepareForGetChildren(oldN);
    NT newNode = myNewTree.prepareForGetChildren(newN);

    if (level >= myNewChildrenLists.size()) {
      myNewChildrenLists.add(new Ref<NT[]>());
      myOldChildrenLists.add(new Ref<OT[]>());
    }

    final Ref<OT[]> oldChildrenR = myOldChildrenLists.get(level);
    final int oldSize = myOldTree.getChildren(oldNode, oldChildrenR);
    final OT[] oldChildren = oldChildrenR.get();

    final Ref<NT[]> newChildrenR = myNewChildrenLists.get(level);
    final int newSize = myNewTree.getChildren(newNode, newChildrenR);
    final NT[] newChildren = newChildrenR.get();

    if (Math.abs(oldSize - newSize) > CHANGE_PARENT_VERSUS_CHILDREN_THRESHOLD) {
      myConsumer.nodeReplaced(oldNode, newNode);
      disposeLevel(oldChildren, oldSize, newChildren, newSize);
      return;
    }

    final ShallowNodeComparator<OT, NT> comparator = myComparator;
    if (oldSize == 0 && newSize == 0) {
      if (!comparator.hashcodesEqual(oldNode, newNode) || !comparator.typesEqual(oldNode, newNode)) {
        myConsumer.nodeReplaced(oldNode, newNode);
      }

      disposeLevel(oldChildren, oldSize, newChildren, newSize);

      return;
    }

    boolean walkedDeep = false;

    ShallowNodeComparator.ThreeState[] deeps;
    if (oldSize == newSize) {
      while (myDeepStates.size() <= level) myDeepStates.add(new ShallowNodeComparator.ThreeState[oldSize]);
      deeps = myDeepStates.get(level);
      if (deeps.length < oldSize) {
        deeps = new ShallowNodeComparator.ThreeState[oldSize];
        myDeepStates.set(level, deeps);
      }
      else {
        Arrays.fill(deeps, 0, oldSize, null);
      }
    }
    else {
      deeps = null;
    }

    int start = 0;
    while (start < oldSize && start < newSize) {
      OT oldChild = oldChildren[start];
      NT newChild = newChildren[start];
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

    if (oldSize == newSize && start == newSize) {
      disposeLevel(oldChildren, oldSize, newChildren, newSize);
      return; // No changes at all at this level
    }

    while (oldEnd >= start && newEnd >= start) {
      OT oldChild = oldChildren[oldEnd];
      NT newChild = newChildren[newEnd];
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
        final OT oldChild = oldChildren[i];
        final NT newChild = newChildren[i];

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
        disposeLevel(oldChildren, oldSize, newChildren, newSize);
        return;
      }

      for (int i = start; i <= oldEnd; i++) {
        final OT oldChild = oldChildren[i];
        myConsumer.nodeDeleted(oldNode, oldChild);
      }

      for (int i = start; i <= newEnd; i++) {
        myConsumer.nodeInserted(oldNode, newChildren[i], i);
      }
    }

    disposeLevel(oldChildren, oldSize, newChildren, newSize);
  }

  private void disposeLevel(final OT[] oldChildren, final int oldSize, final NT[] newChildren, final int newSize) {
    myOldTree.disposeChildren(oldChildren, oldSize);
    myNewTree.disposeChildren(newChildren, newSize);
  }
}
