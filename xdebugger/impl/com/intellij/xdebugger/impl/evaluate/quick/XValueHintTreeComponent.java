package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHintTreeComponent;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;

/**
 * @author nik
 */
public class XValueHintTreeComponent extends AbstractValueHintTreeComponent<Pair<XValue, String>> {
  private final XValueHint myValueHint;
  private final XDebuggerTree myTree;

  public XValueHintTreeComponent(final XValueHint valueHint, final XDebuggerTree tree, final Pair<XValue, String> initialItem) {
    super(valueHint, tree, initialItem);
    myValueHint = valueHint;
    myTree = tree;
    updateTree(initialItem);
  }

  protected void updateTree(final Pair<XValue, String> selectedItem) {
    myTree.setRoot(new XValueNodeImpl(myTree, null, selectedItem.getFirst()), true);
    myValueHint.showTreePopup(this, myTree, selectedItem.getSecond());
  }

  protected void setNodeAsRoot(final Object node) {
    if (node instanceof XValueNodeImpl) {
      final XValueNodeImpl valueNode = (XValueNodeImpl)node;
      myValueHint.shiftLocation();
      Pair<XValue, String> item = Pair.create(valueNode.getValueContainer(), valueNode.getName());
      addToHistory(item);
      updateTree(item);
    }
  }
}
