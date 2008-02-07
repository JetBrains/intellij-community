package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author nik
 */
public class XValueNodeImpl extends XValueContainerNode<XValue> implements XValueNode, XCompositeNode {
  private String myName;
  private String myValue;
  @NonNls private static final String EQ_TEXT = " = ";

  public XValueNodeImpl(XDebuggerTree tree, final XDebuggerTreeNode parent, final XValue value) {
    super(tree, parent, value);
    myText.append(XDebuggerUIConstants.COLLECTING_DATA_MESSAGE, XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
    value.computePresentation(this);
  }

  public void setPresentation(@NotNull final String name, @Nullable final Icon icon, @Nullable final String type, @NotNull final String value,
                              final boolean hasChildren) {
    DebuggerUIUtil.invokeLater(new Runnable() {
      public void run() {
        setIcon(icon);
        myName = name;
        myValue = value;

        myText.clear();
        myText.append(name, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
        myText.append(EQ_TEXT, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        myText.append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setLeaf(!hasChildren);
        fireNodeChanged();
        myTree.nodeLoaded(XValueNodeImpl.this, name, value);
      }
    });
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public String getValue() {
    return myValue;
  }

  public void setValueModificationStarted() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myValue = null;
    myText.clear();
    myText.append(myName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
    myText.append(EQ_TEXT, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    myText.append(XDebuggerUIConstants.MODIFYING_VALUE_MESSAGE, XDebuggerUIConstants.MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES);
    setLeaf(true);
    fireNodeChildrenChanged();
  }
}
