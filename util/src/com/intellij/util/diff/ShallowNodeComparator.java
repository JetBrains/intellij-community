package com.intellij.util.diff;

/**
 * @author max
 */
public interface ShallowNodeComparator<OT, NT> {
  enum ThreeState {
    YES, NO, UNSURE
  }

  ThreeState deepEqual(OT oldNode, NT newNode);
  boolean typesEqual(OT oldNode, NT newNode);
  boolean hashcodesEqual(OT oldNode, NT newNode);
}
