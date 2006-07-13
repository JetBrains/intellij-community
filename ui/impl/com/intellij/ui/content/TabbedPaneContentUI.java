package com.intellij.ui.content;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.UIBundle;
import com.intellij.util.IJSwingUtilities;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.TabbedPaneUI;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * @author Eugene Belyaev
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class TabbedPaneContentUI implements ContentUI, PropertyChangeListener {
  private ContentManager myManager;
  private TabbedPaneWrapper myTabbedPaneWrapper;

  /**
   * Creates <code>TabbedPaneContentUI</code> with bottom tab placement.
   */
  public TabbedPaneContentUI() {
    this(JTabbedPane.BOTTOM);
  }

  /**
   * Creates <code>TabbedPaneContentUI</code> with cpecified tab placement.
   *
   * @param tabPlacement constant which defines where the tabs are located.
   *                     Acceptable values are <code>javax.swing.JTabbedPane#TOP</code>,
   *                     <code>javax.swing.JTabbedPane#LEFT</code>, <code>javax.swing.JTabbedPane#BOTTOM</code>
   *                     and <code>javax.swing.JTabbedPane#RIGHT</code>.
   */
  public TabbedPaneContentUI(int tabPlacement) {
    myTabbedPaneWrapper = new MyTabbedPaneWrapper(tabPlacement);
  }

  public JComponent getComponent() {
    return myTabbedPaneWrapper.getComponent();
  }

  public void setManager(ContentManager manager) {
    if (myManager != null) {
      throw new IllegalStateException();
    }
    myManager = manager;
    myManager.addContentManagerListener(new MyContentManagerListener());
  }

  public void propertyChange(PropertyChangeEvent e) {
    if (Content.PROP_DISPLAY_NAME.equals(e.getPropertyName())) {
      Content content = (Content)e.getSource();
      int index = myTabbedPaneWrapper.indexOfComponent(content.getComponent());
      if (index != -1) {
        myTabbedPaneWrapper.setTitleAt(index, content.getTabName());
      }
    }
    else if (Content.PROP_DESCRIPTION.equals(e.getPropertyName())) {
      Content content = (Content)e.getSource();
      int index = myTabbedPaneWrapper.indexOfComponent(content.getComponent());
      if (index != -1) {
        myTabbedPaneWrapper.setToolTipTextAt(index, content.getDescription());
      }
    }
    else if (Content.PROP_COMPONENT.equals(e.getPropertyName())) {
      Content content = (Content)e.getSource();
      JComponent oldComponent = (JComponent)e.getOldValue();
      int index = myTabbedPaneWrapper.indexOfComponent(oldComponent);
      if (index != -1) {
        boolean hasFocus = IJSwingUtilities.hasFocus2(oldComponent);
        myTabbedPaneWrapper.setComponentAt(index, content.getComponent());
        if (hasFocus) {
          (content.getComponent()).requestDefaultFocus();
        }
      }
    }
    else if (Content.PROP_ICON.equals(e.getPropertyName())) {
      Content content = (Content)e.getSource();
      int index = myTabbedPaneWrapper.indexOfComponent(content.getComponent());
      if (index != -1) {
        myTabbedPaneWrapper.setIconAt(index, (Icon)e.getNewValue());
      }
    }
  }

  private Content getSelectedContent() {
    JComponent selectedComponent = myTabbedPaneWrapper.getSelectedComponent();
    return myManager.getContent(selectedComponent);
  }

  /**
   * Removes specified content.
   */
  private class CloseAction extends AnAction {
    private Content myContent;

    public CloseAction(Content content) {
      myContent = content;
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ACTIVE_TAB));
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.removeContent(myContent);
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myContent != null && myManager.canCloseContents());
      presentation.setVisible(myManager.canCloseContents());
      presentation.setText(myManager.getCloseActionName());
    }
  }

  /**
   * Removes all contents.
   */
  private class CloseAllAction extends AnAction {
    public CloseAllAction() {
      super(UIBundle.message("tabbed.pane.close.all.action.name"));
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.removeAllContents();
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myManager.canCloseAllContents());
      presentation.setVisible(myManager.canCloseAllContents());
    }
  }

  /**
   * Removes all contents but specified.
   */
  private class CloseAllButThisAction extends AnAction {
    private Content myContent;

    public CloseAllButThisAction(Content content) {
      super(UIBundle.message("tabbed.pane.close.all.but.this.action.name"));
      myContent = content;
    }

    public void actionPerformed(AnActionEvent e) {
      Content[] contents = myManager.getContents();
      for (int i = 0; i < contents.length; i++) {
        Content content = contents[i];
        if (myContent != content) {
          myManager.removeContent(content);
        }
      }
      myManager.setSelectedContent(myContent);
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setText(myManager.getCloseAllButThisActionName());
      presentation.setEnabled(myContent != null && myManager.canCloseContents() && myManager.getContentCount() > 1);
      presentation.setVisible(myManager.canCloseContents());
    }
  }

  /**
   * Pins tab that corresponds to the content
   */
  private class MyPinTabAction extends ToggleAction {
    private Content myContent;

    public MyPinTabAction(Content content) {
      myContent = content;
      Presentation presentation = getTemplatePresentation();
      presentation.setText(UIBundle.message("tabbed.pane.pin.tab.action.name"));
      presentation.setDescription(UIBundle.message("tabbed.pane.pin.tab.action.description"));
    }

    public boolean isSelected(AnActionEvent event) {
      return myContent != null && myContent.isPinned();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      myContent.setPinned(flag);
    }

    public void update(AnActionEvent event) {
      super.update(event);
      Presentation presentation = event.getPresentation();
      boolean enabled = myContent != null && myContent.isPinnable();
      presentation.setEnabled(enabled);
      presentation.setVisible(enabled);
    }
  }

  private final class MyNextTabAction extends AnAction {
    public MyNextTabAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_TAB));
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.selectNextContent();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myManager.getContentCount() > 1);
    }
  }

  private final class MyPreviousTabAction extends AnAction {
    public MyPreviousTabAction() {
      copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_TAB));
    }

    public void actionPerformed(AnActionEvent e) {
      myManager.selectPreviousContent();
    }

    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myManager.getContentCount() > 1);
    }
  }


  private class MyTabbedPaneWrapper extends TabbedPaneWrapper {
    public MyTabbedPaneWrapper(int tabPlacement) {
      super(tabPlacement);
    }

    protected TabbedPaneWrapper.TabbedPane createTabbedPane(int tabPlacement) {
      return new MyTabbedPane(tabPlacement);
    }

    protected TabbedPaneHolder createTabbedPaneHolder() {
      return new MyTabbedPaneHolder();
    }

    private class MyTabbedPane extends TabbedPane {
      public MyTabbedPane(int tabPlacement) {
        super(tabPlacement);
        addMouseListener(new MyPopupHandler());
        enableEvents(MouseEvent.MOUSE_EVENT_MASK);
      }

      private void closeTabAt(int x, int y) {
        TabbedPaneUI ui = getUI();
        int index = ui.tabForCoordinate(this, x, y);
        if (index < 0 || !myManager.canCloseContents()) {
          return;
        }
        myManager.removeContent(myManager.getContent(index));
      }

      /**
       * Hides selected menu.
       */
      private void hideMenu() {
        MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
        menuSelectionManager.clearSelectedPath();
      }

      protected void processMouseEvent(MouseEvent e) {
        if (e.isPopupTrigger()) { // Popup doesn't activate clicked tab.
          showPopup(e.getX(), e.getY());
          return;
        }

        if (!e.isShiftDown() && (MouseEvent.BUTTON1_MASK & e.getModifiers()) > 0) { // RightClick without Shift modifiers just select tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            TabbedPaneUI ui = getUI();
            int index = ui.tabForCoordinate(this, e.getX(), e.getY());
            if (index != -1) {
              setSelectedIndex(index);
            }
            hideMenu();
          }
        }
        else if (e.isShiftDown() && (MouseEvent.BUTTON1_MASK & e.getModifiers()) > 0) { // Shift+LeftClick closes the tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            closeTabAt(e.getX(), e.getY());
            hideMenu();
          }
        }
        else if ((MouseEvent.BUTTON2_MASK & e.getModifiers()) > 0) { // MouseWheelClick closes the tab
          if (MouseEvent.MOUSE_RELEASED == e.getID()) {
            closeTabAt(e.getX(), e.getY());
            hideMenu();
          }
        }
        else if ((MouseEvent.BUTTON3_MASK & e.getModifiers()) > 0 && SystemInfo.isWindows) { // Right mouse button doesn't activate tab
        }
        else {
          super.processMouseEvent(e);
        }
      }

      protected ChangeListener createChangeListener() {
        return new MyModelListener();
      }

      private class MyModelListener extends JTabbedPane.ModelListener {
        public void stateChanged(ChangeEvent e) {
          Content content = getSelectedContent();
          if (content != null) {
            myManager.setSelectedContent(content);
          }
          super.stateChanged(e);
        }
      }

      /**
       * @return content at the specified location.  <code>x</code> and <code>y</code> are in
       *         tabbed pane coordinate system. The method returns <code>null</code> if there is no contnt at the
       *         specified location.
       */
      private Content getContentAt(int x, int y) {
        TabbedPaneUI ui = getUI();
        int index = ui.tabForCoordinate(this, x, y);
        if (index < 0) {
          return null;
        }
        return myManager.getContent(index);
      }

      protected class MyPopupHandler extends PopupHandler {
        public void invokePopup(Component comp, int x, int y) {
          if (myManager.getContentCount() == 0) return;
          showPopup(x, y);
        }
      }

      /**
       * Shows showPopup menu at the specified location. The <code>x</code> and <code>y</code> coordinates
       * are in JTabbedPane coordinate system.
       */
      private void showPopup(int x, int y) {
        Content content = getContentAt(x, y);
        if (content == null) {
          return;
        }
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new CloseAction(content));
        if (myTabbedPaneWrapper.getTabCount() > 1) {
          group.add(new CloseAllAction());
          group.add(new CloseAllButThisAction(content));
        }
        group.addSeparator();
        group.add(new MyPinTabAction(content));
        group.addSeparator();
        group.add(new MyNextTabAction());
        group.add(new MyPreviousTabAction());
        final List<AnAction> additionalActions = myManager.getAdditionalPopupActions(content);
        if (additionalActions != null) {
          group.addSeparator();
          for (AnAction anAction : additionalActions) {
            group.add(anAction);
          }
        }
        ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        menu.getComponent().show(myTabbedPaneWrapper.getComponent(), x, y);
      }
    }

    private class MyTabbedPaneHolder extends TabbedPaneHolder implements DataProvider {
      public Object getData(String dataId) {
        if (DataConstantsEx.CONTENT_MANAGER.equals(dataId)) {
          return myManager;
        }
        return null;
      }
    }
  }

  private class MyContentManagerListener extends ContentManagerAdapter {
    public void contentAdded(ContentManagerEvent event) {
      Content content = event.getContent();
      myTabbedPaneWrapper.insertTab(content.getTabName(),
                                    content.getIcon(),
                                    content.getComponent(),
                                    content.getDescription(),
                                    event.getIndex());
      content.addPropertyChangeListener(TabbedPaneContentUI.this);
    }

    public void contentRemoved(ContentManagerEvent event) {
      event.getContent().removePropertyChangeListener(TabbedPaneContentUI.this);
      myTabbedPaneWrapper.removeTabAt(event.getIndex());
    }

    public void selectionChanged(ContentManagerEvent event) {
      myTabbedPaneWrapper.setSelectedIndex(event.getIndex());
    }
  }
}
