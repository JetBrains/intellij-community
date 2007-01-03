package com.intellij.util.diff;

import com.intellij.util.ThreeState;

/**
 * @author max
 */
public interface ShallowNodeComparator<OT, NT> {

  ThreeState deepEqual(OT oldNode, NT newNode);
  boolean typesEqual(OT oldNode, NT newNode);
  boolean hashcodesEqual(OT oldNode, NT newNode);
}
