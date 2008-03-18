package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class SetValueInplaceEditor extends XDebuggerTreeInplaceEditor {
  private JPanel myEditorPanel;
  private XValueModifier myModifier;
  private final XValueNodeImpl myValueNode;

  public SetValueInplaceEditor(final XValueNodeImpl node, @NotNull final String nodeName) {
    super(node, "setValue");
    myValueNode = node;
    myModifier = myValueNode.getValueContainer().getModifier();

    myEditorPanel = new JPanel();
    myEditorPanel.setLayout(new BoxLayout(myEditorPanel, BoxLayout.X_AXIS));
    SimpleColoredComponent nameLabel = new SimpleColoredComponent();
    nameLabel.setIcon(getNode().getIcon());
    nameLabel.append(nodeName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);

    myEditorPanel.add(nameLabel);

    myEditorPanel.add(myExpressionEditor.getComponent());
    myExpressionEditor.selectAll();
  }

  protected JComponent createInplaceEditorComponent() {
    return myEditorPanel;
  }

  public void doOKAction() {
    myExpressionEditor.saveTextInHistory();
    final XDebuggerTreeState treeState = XDebuggerTreeState.saveState(myTree);
    myValueNode.setValueModificationStarted();
    myModifier.setValue(myExpressionEditor.getText(), new XValueModifier.XModificationCallback() {
      public void valueModified() {
        DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
          public void run() {
            myTree.rebuildAndRestore(treeState);
          }
        });
      }

      public void errorOccured(@NotNull final String errorMessage) {
        DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
          public void run() {
            myTree.rebuildAndRestore(treeState);
            //todo[nik] show hint instead
            Messages.showErrorDialog(myTree, errorMessage);
          }
        });
      }
    });
    super.doOKAction();
  }
}
