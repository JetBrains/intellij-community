/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.*;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.PopupHandler;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;

public abstract class DebuggerPanel extends JPanel implements DataProvider{
  private final Project myProject;
  private final DebuggerTree myTree;
  private final DebuggerStateManager myStateManager;
  private final DebuggerContextListener myContextListener;
  private int myEvent = DebuggerSession.EVENT_REFRESH;
  private boolean myNeedsRefresh = true;

  public DebuggerPanel(Project project, DebuggerStateManager stateManager) {
    super(new BorderLayout());
    myProject = project;
    myStateManager = stateManager;
    myTree = createTreeView();
    myContextListener = new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        DebuggerPanel.this.changeEvent(newContext, event);
      }
    };

    myTree.addMouseListener(new PopupHandler(){
      public void invokePopup(Component comp,int x,int y){
        TreePath path = myTree.getLeadSelectionPath();

        ActionPopupMenu popupMenu = createPopupMenu(
        );
        if (popupMenu != null) {
          popupMenu.getComponent().show(comp, x, y);
        }
      }
    });
    setFocusTraversalPolicy(new IdeFocusTraversalPolicy() {
      public Component getDefaultComponentImpl(Container focusCycleRoot) {
        return myTree;
      }
    });
    myStateManager.addListener(myContextListener);
  }

  protected abstract DebuggerTree createTreeView();

  protected void changeEvent(DebuggerContextImpl newContext, int event) {
    if(newContext.getDebuggerSession() == null) return;

    rebuildWhenVisible(event);
  }

  protected boolean shouldRebuildNow() {
    return true;
  }

  public boolean isNeedsRefresh() {
    return myNeedsRefresh;
  }

  public final void rebuildWhenVisible() {
    rebuildWhenVisible(myEvent);
  }

  protected final void rebuildWhenVisible(int event) {
    myEvent = event;
    if(!shouldRebuildNow()) {
      myNeedsRefresh = true;
    } else {
      myNeedsRefresh = false;
      rebuild(event);
    }
  }

  protected void rebuild(int event) {
    DebuggerSession debuggerSession = getContext().getDebuggerSession();
    if(debuggerSession == null) return;

    getTree().rebuild(getContext());
  }

  protected void showMessage(MessageDescriptor descriptor) {
    myTree.showMessage(descriptor);
  }

  public void dispose() {
    myStateManager.removeListener(myContextListener);
    myTree.dispose();
  }

  protected abstract ActionPopupMenu createPopupMenu();

  public DebuggerContextImpl getContext() {
    return myStateManager.getContext();
  }

  protected DebuggerTree getTree() {
    return myTree;
  }

  public void clear() {
    myTree.removeAllChildren();
  }

  protected final Project getProject() {
    return myProject;
  }

  public DebuggerStateManager getContextManager() {
    return myStateManager;
  }

  public Object getData(String dataId) {
    if (DebuggerActions.DEBUGGER_PANEL.equals(dataId)) {
      return this;
    }
    return null;
  }
}
