package com.intellij.xdebugger.impl.evaluate;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.Tree;
import com.intellij.xdebugger.XDebuggerBundle;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * @author nik
 */
public abstract class AbstractValueHintTreeComponent<H> extends JPanel {
  private static final Icon ICON_FWD = IconLoader.getIcon("/actions/forward.png");
  private static final Icon ICON_BACK = IconLoader.getIcon("/actions/back.png");
  private static final Icon ICON_UNMARK_WEBROOT = IconLoader.getIcon("/modules/unmarkWebroot.png");
  private static final int HISTORY_SIZE = 11;
  private ArrayList<H> myHistory = new ArrayList<H>();
  private int myCurrentIndex = -1;
  private AbstractValueHint myValueHint;
  private Tree myTree;

  protected AbstractValueHintTreeComponent(LayoutManager layout, final AbstractValueHint valueHint, final Tree tree, final H initialItem) {
    super(layout);
    myValueHint = valueHint;
    myTree = tree;
    myHistory.add(initialItem);
    add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.NORTH);
  }

  private AnAction createGoForwardAction(){
    return new AnAction(CodeInsightBundle.message("quick.definition.forward"), null, ICON_FWD){
      public void actionPerformed(AnActionEvent e) {
        if (myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1){
          myCurrentIndex ++;
          updateHint();
        }
      }


      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex < myHistory.size() - 1);
      }
    };
  }

  private void updateHint() {
    myValueHint.shiftLocation();
    updateTree(myHistory.get(myCurrentIndex));
  }

  private AnAction createGoBackAction(){
    return new AnAction(CodeInsightBundle.message("quick.definition.back"), null, ICON_BACK){
      public void actionPerformed(AnActionEvent e) {
        if (myHistory.size() > 1 && myCurrentIndex > 0) {
          myCurrentIndex--;
          updateHint();
        }
      }


      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myHistory.size() > 1 && myCurrentIndex > 0);
      }
    };
  }

  protected abstract void updateTree(H item);

  protected void addToHistory(final H item) {
    if (myCurrentIndex < HISTORY_SIZE) {
      if (myCurrentIndex != -1) {
        myCurrentIndex += 1;
      } else {
        myCurrentIndex = 1;
      }
      myHistory.add(myCurrentIndex, item);
    }
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(createSetRoot());

    AnAction back = createGoBackAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, KeyEvent.ALT_MASK)), this);
    group.add(back);

    AnAction forward = createGoForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, KeyEvent.ALT_MASK)), this);
    group.add(forward);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
  }

  private AnAction createSetRoot() {
    final String title = XDebuggerBundle.message("xdebugger.popup.value.tree.set.root.action.tooltip");
    return new AnAction(title, title, ICON_UNMARK_WEBROOT) {
      public void actionPerformed(AnActionEvent e) {
        final TreePath path = myTree.getSelectionPath();
        if (path == null) return;
        final Object node = path.getLastPathComponent();
        setNodeAsRoot(node);
      }
    };
  }

  protected abstract void setNodeAsRoot(Object node);
}
