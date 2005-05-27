package com.intellij.openapi.fileEditor.impl;

import com.intellij.Patches;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.plaf.beg.BegTabbedPaneUI;

import javax.swing.*;
import javax.swing.plaf.TabbedPaneUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class EditorTabbedContainer extends TabbedPaneWrapper {
  private final EditorWindow myWindow;
  private final Project myProject;
  private final FileEditorManagerImpl myEditorManager;

  EditorTabbedContainer(final EditorWindow window, Project project, int tabPlacement, FileEditorManagerImpl editorManager) {
    super(tabPlacement);
    myWindow = window;
    myProject = project;
    myEditorManager = editorManager;
  }

  protected TabbedPane createTabbedPane(int tabPlacement) {
    return new MyTabbedPane(tabPlacement);

  }

  protected TabbedPaneHolder createTabbedPaneHolder() {
    return new MyTabbedPaneHolder();
  }

  private final class MyTabbedPane extends TabbedPaneWrapper.TabbedPane {
    private final MyTabbedPanePopupHandler myTabbedPanePopupHandler;
    private int myLastClickedIndex;

    public MyTabbedPane(int tabPlacement) {
      super(tabPlacement);
      enableEvents(MouseEvent.MOUSE_EVENT_MASK);
      setOpaque(true);
      myTabbedPanePopupHandler = new MyTabbedPanePopupHandler();
//      putClientProperty("TabbedPane.paintContentBorder", Boolean.FALSE);
      setFocusable(false);
      updateUI();
    }

    /**
     * Actually all this stuff should be placed into updateUI method.
     * But due to this method replaces the current UI of the tabbed pane
     * this code exists here. One I [vova] placed it into updateUI and
     * middle mouse button stop working in SCROLL_TAB_LAYOUT.
     */
    public void setTabLayoutPolicy(int tabLayoutPolicy) {
      super.setTabLayoutPolicy(tabLayoutPolicy);
      if (Patches.SUN_BUG_ID_4620537) {
        TabbedPaneUI tabbedPaneUI = getUI();
        if (!(tabbedPaneUI instanceof BasicTabbedPaneUI)) {
          return;
        }
        for (int i = 0; i < getComponentCount(); i++) {
          Component c = getComponent(i);
          if (!(c instanceof JViewport)) {
            continue;
          }
          JPanel panel = (JPanel)((JViewport)c).getView();
          panel.setBackground(UIManager.getColor("TabbedPane.background"));

          MouseListener[] listeners = (MouseListener[])panel.getListeners(MouseListener.class);
          for (int j = 0; j < listeners.length; j++) {
            panel.removeMouseListener(listeners[j]);
          }
        }
      }
    }

    private VirtualFile getFileAt(final int x, final int y) {
      final int index = getTabIndexAt(x, y);
      if (index < 0) {
        return null;
      }
      return myWindow.getEditors()[index].getFile();
    }

    protected void processMouseEvent(MouseEvent e) {
      // First of all we have to hide showing popup menu (if any)
      if (MouseEvent.MOUSE_PRESSED == e.getID()) {
        final MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
        menuSelectionManager.clearSelectedPath();
        myWindow.setAsCurrentWindow(true);
      }

      // activate current tabbed pane:
      //if (MouseEvent.MOUSE_RELEASED == e.getID ()) {
      //}

      if (e.isPopupTrigger()) { // Popup doesn't activate clicked tab
        myTabbedPanePopupHandler.invokePopup(e.getComponent(), e.getX(), e.getY());
        return;
      }

      if (!e.isShiftDown() && (MouseEvent.BUTTON1_MASK & e.getModifiers()) > 0) {
        // RightClick without Shift modifiers just select tab
        // To reach this behaviour we have to block all MOUSE_PRESSED events
        // and react only on MOUSE_RELEASE. But clicks outside tab bounds
        // have a special sense under Mac OS X. There is a special scroll button
        // in Aqua LAF which shows drop down list with opened tab. To make it
        // work we also have to specially hande clicks outsode the tab bounds.
        
        if (MouseEvent.MOUSE_PRESSED == e.getID()) {
          // use index from mouse pressed event, cause when MOUSE_release event is dispatched, 
          // the tab may shift because the toolwindow at the left will hide, 
          // so the situation is: the mouse is pressed over one tab and released over another tab (or even outside the tab area)
          myLastClickedIndex = getTabIndexAt(e.getX(), e.getY());
        }
        
        if (MouseEvent.MOUSE_RELEASED == e.getID()) {
          if (myLastClickedIndex != -1) {
            setSelectedIndex(myLastClickedIndex);
          }
          else {
            // push forward events outside thw tab bounds
            super.processMouseEvent(e);
          }
        }
        else {
          if (myLastClickedIndex == -1) {
            // push forward events outside thw tab bounds
            super.processMouseEvent(e);
          }
        }
      }
      else if (e.isShiftDown() && (MouseEvent.BUTTON1_MASK & e.getModifiers()) > 0) { // Shift+LeftClick closes the tab
        if (MouseEvent.MOUSE_RELEASED == e.getID()) {
          final VirtualFile file = getFileAt(e.getX(), e.getY());
          if (file != null) {
            myWindow.closeFile(file);
          }
        }
      }
      else if ((MouseEvent.BUTTON2_MASK & e.getModifiers()) > 0) { // MouseWheelClick closes the tab
        if (MouseEvent.MOUSE_RELEASED == e.getID()) {
          final VirtualFile file = getFileAt(e.getX(), e.getY());
          if (file != null) {
            myWindow.closeFile(file);
          }
        }
      }
      else if ((MouseEvent.BUTTON3_MASK & e.getModifiers()) > 0 && SystemInfo.isWindows) { // Right mouse button doesn't activate tab
      }
      else {
        super.processMouseEvent(e);
      }
    }

    public void paint(Graphics g) {
      if (getTabCount() != 0) {
        super.paint(g);
      }
      else {
        g.setColor(Color.gray);
        g.fillRect(0, 0, getWidth(), getHeight());
      }
    }

    public void updateUI() {
      super.updateUI();
      if (getUI() instanceof BegTabbedPaneUI) {
        ((BegTabbedPaneUI)getUI()).setNoIconSpace(UISettings.getInstance().MARK_MODIFIED_TABS_WITH_ASTERISK);
      }
    }



    /**
     * Closes all editor in the tabbed container
     */
    private final class MyCloseAllAction extends AnAction {
      public MyCloseAllAction() {
        super("Close All", "Close all editors in the tab group", null);
        registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS).getShortcutSet(), null);
      }

      public void actionPerformed(AnActionEvent e) {
        // Collect files to be closed
        final VirtualFile[] files = myWindow.getFiles();
        for (int i = 0; i < files.length; i++) {
          myWindow.closeFile(files[i]);
        }
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(getTabCount() > 0);
      }
    }


    private final class MyTabbedPanePopupHandler extends PopupHandler {
      public void invokePopup(final Component comp, final int x, final int y) {
        final ActionGroup group = (ActionGroup)CustomizableActionsSchemas.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_TAB_POPUP);
        final ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_TAB_POPUP, group);
        menu.getComponent().show(comp, x, y);
      }
    }
  }

  private final class MyTabbedPaneHolder extends TabbedPaneWrapper.TabbedPaneHolder implements DataProvider {
    public Object getData(String dataId) {
      if (DataConstants.VIRTUAL_FILE.equals(dataId)) {
        return myWindow.getSelectedFile();
      }
      if (DataConstantsEx.EDITOR_WINDOW.equals(dataId)) {
        return myWindow;
      }
      if (DataConstants.HELP_ID.equals(dataId)) {
        return "ideaInterface.editor";
      }
      return null;
    }
  }

}
