package com.intellij.openapi.fileEditor.impl;

import com.intellij.Patches;
import com.intellij.ide.actions.NextTabAction;
import com.intellij.ide.actions.PreviousTabAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.fileEditor.FileEditorManager;
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

  protected JComponent createTabbedPaneHolder() {
    return new MyTabbedPaneHolder();
  }

  private final class MyTabbedPane extends TabbedPaneWrapper.TabbedPane {
    private final MyTabbedPanePopupHandler myTabbedPanePopupHandler;

    public MyTabbedPane(int tabPlacement) {
      super(tabPlacement);
      enableEvents(MouseEvent.MOUSE_EVENT_MASK);
      setOpaque(true);
      myTabbedPanePopupHandler = new MyTabbedPanePopupHandler();
      putClientProperty("TabbedPane.paintContentBorder", Boolean.FALSE);
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
        final int index = getTabIndexAt(e.getX(), e.getY());
        if (MouseEvent.MOUSE_RELEASED == e.getID()) {
          if (index != -1) {
            setSelectedIndex(index);
          }
          else { // push forward events outside thw tab bounds
            super.processMouseEvent(e);
          }
        }
        else {
          if (index == -1) { // push forward events outside thw tab bounds
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


    private final class MyCloseAction extends AnAction {
      private final VirtualFile myFile;

      public MyCloseAction(final VirtualFile file) {
        super("Close");
        registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_EDITOR).getShortcutSet(), null);
        myFile = file;
      }

      public void actionPerformed(final AnActionEvent e) {
        myWindow.closeFile(myFile);
      }

      public void update(final AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(myFile != null);
      }
    }

    private final class MyCloseAllButThisAction extends AnAction {
      private final VirtualFile myFile;

      public MyCloseAllButThisAction(VirtualFile file) {
        super("Close All But This");
        registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_EDITORS_BUT_THIS).getShortcutSet(), null);
        myFile = file;
      }

      public void actionPerformed(AnActionEvent e) {
        final VirtualFile[] siblings = myWindow.getFiles();
        for (int i = 0; i < siblings.length; i++) {
          VirtualFile file = siblings[i];
          if (file != myFile) {
            myWindow.closeFile(siblings[i]);
          }
        }
      }

      public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(myFile != null && getTabCount() > 1);
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

    /**
     * Closes all editor in the tabbed container
     */
    private final class MyCloseAllUnmodifiedAction extends AnAction {
      public MyCloseAllUnmodifiedAction() {
        super("Close All Unmodified", "Close all editors in the tab group", null);
        registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ALL_UNMODIFIED_EDITORS).getShortcutSet(), null);
      }

      public void actionPerformed(AnActionEvent e) {
        // Collect files to be closed
        final EditorWithProviderComposite[] editors = myWindow.getEditors();
        for (int i = 0; i < editors.length; i++) {
          final EditorWithProviderComposite editor = editors[i];
          if (!myEditorManager.isChanged(editor)) {
            myWindow.closeFile(editor.getFile ());
          }
        }
      }

      public void update(AnActionEvent e) {
        final EditorWithProviderComposite[] editors = myWindow.getEditors();
        for (int i = 0; i < editors.length; i++) {
          if (!myEditorManager.isChanged(editors[i])) {
            e.getPresentation().setEnabled(true);
            return;
          }
        }
        e.getPresentation().setEnabled(false);
      }
    }

    /**
     * Toggles "pin" state of the file
     */
    private final class MyPinEditorAction extends AnAction {
      private final VirtualFile myFile;

      public MyPinEditorAction(VirtualFile file) {
        getTemplatePresentation().setDescription("Pin/unpin editor tab");
        myFile = file;
      }

      public void actionPerformed(AnActionEvent e) {
        myWindow.setFilePinned(myFile, !myWindow.isFilePinned(myFile));
      }

      public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setText("Pin Tab");
        if (myFile == null) {
          presentation.setEnabled(false);
        }
        else {
          presentation.setEnabled(true);
          if (myWindow.isFilePinned(myFile)) {
            presentation.setText("Unpin Tab");
          }
        }
      }
    }

    /**
     * Selects next tab
     */
    private final class MyNextTabAction extends AnAction {
      public MyNextTabAction() {
        copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB));
      }

      public void actionPerformed(AnActionEvent e) {
        final EditorComposite selectedComposite = myWindow.getSelectedEditor();
        NextTabAction.navigateImpl(myProject, selectedComposite.getFile(), +1);
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myTabbedPane.getTabCount() > 1);
      }
    }

    private final class MyPreviousTabAction extends AnAction {
      public MyPreviousTabAction() {
        copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB));
      }

      public void actionPerformed(AnActionEvent e) {
        final EditorComposite selectedComposite = myWindow.getSelectedEditor();
        PreviousTabAction.navigateImpl(myProject, selectedComposite.getFile(), -1);
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myTabbedPane.getTabCount() > 1);
      }
    }

    private abstract class MyNewTabGroupAction extends AnAction {
      private final int myOrientation;
      private final VirtualFile myFile;

      protected MyNewTabGroupAction(int orientation, VirtualFile file) {
        myOrientation = orientation;
        myFile = file;
      }

      public void actionPerformed(AnActionEvent e) {
        myEditorManager.createSplitter(myOrientation);
      }

      public void update(AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(myEditorManager.hasOpenedFile());
      }
    }

    private final class MyNewHorizontalTabGroupAction extends MyNewTabGroupAction {
      public MyNewHorizontalTabGroupAction(VirtualFile file) {
        super(SwingConstants.HORIZONTAL, file);
        final AnAction action = ActionManager.getInstance().getAction("SplitHorisontal");
        copyFrom(action);
      }
    }

    private final class MyNewVerticalTabGroupAction extends MyNewTabGroupAction {
      public MyNewVerticalTabGroupAction(VirtualFile file) {
        super(SwingConstants.VERTICAL, file);
        final AnAction action = ActionManager.getInstance().getAction("SplitVertical");
        copyFrom(action);
      }
    }

    private final class MyMoveEditorToOppositeTabGroupAction extends AnAction{
      private final VirtualFile myFile;

      public MyMoveEditorToOppositeTabGroupAction(final VirtualFile file) {
        super ("Move To Opposite Tab Group");
        myFile = file;
      }

      public void actionPerformed(final AnActionEvent event) {
        if(myFile != null && myWindow != null){
          final EditorWindow[] siblings = myWindow.findSiblings ();
          if (siblings != null && siblings.length == 1) {
            FileEditorManagerImpl.openFileImpl3 (siblings [0], myFile, true, null);
            myWindow.closeFile(myFile);
          }
        }
      }

      public void update(AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        if(myFile != null && myWindow != null){
          final EditorWindow[] siblings = myWindow.findSiblings ();
          if (siblings != null && siblings.length == 1)
            presentation.setEnabled(true);
          else
            presentation.setEnabled(false);
          return;
        }
        presentation.setEnabled(false);
      }
    }

    private final class MyChangeTabGroupsOrientation extends AnAction {
      public MyChangeTabGroupsOrientation() {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_CHANGE_SPLIT_ORIENTATION);
        copyFrom(action);
      }

      public void actionPerformed(AnActionEvent e) {
        myEditorManager.changeSplitterOrientation();
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myEditorManager.isInSplitter());
      }
    }

    private final class MyTabbedPanePopupHandler extends PopupHandler {
      public void invokePopup(final Component comp, final int x, final int y) {
        final DefaultActionGroup _group = new DefaultActionGroup();
        final ActionManager actionManager = ActionManager.getInstance();
        final VirtualFile fileAt = getFileAt(x, y);
        _group.add(new MyCloseAction(fileAt));
        _group.add(new MyCloseAllButThisAction(fileAt));
        _group.add(new MyCloseAllAction());
        _group.add(new MyCloseAllUnmodifiedAction());
        _group.addSeparator();
        _group.add(new MyNewHorizontalTabGroupAction(fileAt));
        _group.add(new MyNewVerticalTabGroupAction(fileAt));
        _group.add(new MyMoveEditorToOppositeTabGroupAction(fileAt));
        _group.add(new MyChangeTabGroupsOrientation());
        //_group.addSeparator();
        _group.add(new MyPinEditorAction(fileAt));
        _group.addSeparator();
        _group.add(new MyNextTabAction());
        _group.add(new MyPreviousTabAction());
        _group.addSeparator();
        final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_EDITOR_TAB_POPUP);
        final AnAction[] children = group.getChildren(null);
        for (int i = 0; i < children.length; i++) {
          final AnAction child = children[i];
          _group.add(child);
        }
        final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.EDITOR_TAB_POPUP, _group);
        menu.getComponent().show(comp, x, y);
      }
    }
  }

  private final class MyTabbedPaneHolder extends TabbedPaneWrapper.TabbedPaneHolder implements DataProvider {
    public Object getData(String dataId) {
      if (DataConstants.VIRTUAL_FILE.equals(dataId)) {
        return myWindow.getSelectedFile();
      }
      else {
        return null;
      }
    }
  }
}
