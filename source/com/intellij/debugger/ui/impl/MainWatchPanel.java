/**
 * created at Dec 17, 2001
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.ui.*;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListenerUtil;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MainWatchPanel extends WatchPanel implements DataProvider {
  private KeyStroke myRemoveWatchAccelerator = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
  private KeyStroke myNewWatchAccelerator = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0);
  private KeyStroke myEditWatchAccelerator = KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0);

  public MainWatchPanel(Project project, DebuggerStateManager stateManager) {
    super(project,stateManager);
    AnAction removeWatchesAction = ActionManager.getInstance().getAction(DebuggerActions.REMOVE_WATCH);
    removeWatchesAction.registerCustomShortcutSet(new CustomShortcutSet(myRemoveWatchAccelerator), getWatchTree());

    AnAction newWatchAction  = ActionManager.getInstance().getAction(DebuggerActions.NEW_WATCH);
    newWatchAction.registerCustomShortcutSet(new CustomShortcutSet(myNewWatchAccelerator), getWatchTree());

    ListenerUtil.addMouseListener(getWatchTree(), new MouseAdapter(){
      public void mouseClicked(MouseEvent e) {
        if(e.getButton() == MouseEvent.BUTTON1 &&  e.getClickCount() == 2) {
          AnAction editWatchAction  = ActionManager.getInstance().getAction(DebuggerActions.EDIT_WATCH);
          Presentation presentation = (Presentation)editWatchAction.getTemplatePresentation().clone();
          DataContext context = DataManager.getInstance().getDataContext(getWatchTree());

          AnActionEvent actionEvent = new AnActionEvent(null, context, "WATCH_TREE", presentation, ActionManager.getInstance(), 0);
          editWatchAction.actionPerformed(actionEvent);
        }
      }
    });

    AnAction editWatchAction  = ActionManager.getInstance().getAction(DebuggerActions.EDIT_WATCH);
    editWatchAction.registerCustomShortcutSet(new CustomShortcutSet(myEditWatchAccelerator), getWatchTree());
  }

  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.WATCH_PANEL_POPUP);
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(DebuggerActions.WATCH_PANEL_POPUP, group);
    return popupMenu;
  }

  public void newWatch() {
    final DebuggerTreeNodeImpl node = getWatchTree().addWatch(TextWithImportsImpl.EMPTY);

    editNode(node);
  }

  public void editNode(final DebuggerTreeNodeImpl node) {
    final DebuggerContextImpl context = getContext();
    final DebuggerExpressionComboBox comboBox = new DebuggerExpressionComboBox(getProject(), PositionUtil.getContextElement(context), "evaluation");
    comboBox.setText((TextWithImportsImpl)((WatchItemDescriptor)node.getDescriptor()).getEvaluationText());

    DebuggerTree.InplaceEditor editor = new DebuggerTree.InplaceEditor(node) {
      public JComponent createEditorComponent() {
        return comboBox;
      }

      public JComponent getContentComponent() {
        return comboBox.getPreferredFocusedComponent();
      }

      public Editor getEditor() {
        return comboBox.getEditor();
      }

      public void doOKAction() {
        TextWithImportsImpl text = comboBox.getText();
        WatchDebuggerTree.setWatchNodeText(node, text);
        comboBox.addRecent(text);
        super.doOKAction();
      }
    };
    editor.show();
  }
}
