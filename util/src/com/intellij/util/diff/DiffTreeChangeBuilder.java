package com.intellij.util.diff;

/**
 * @author max
 */
public interface DiffTreeChangeBuilder<OT, NT> {
  void nodeReplaced(OT oldChild, NT newChild);
  void nodeDeleted(OT oldParent, OT oldNode);
  void nodeInserted(OT oldParent, NT newNode, int pos);
}
