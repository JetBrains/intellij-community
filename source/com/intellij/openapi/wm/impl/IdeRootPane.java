package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.wm.ex.ActionToolbarEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.KeymapManager;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

// Made public and non-final for Fabrique
public class IdeRootPane extends JRootPane{
  /**
   * Toolbar and status bar.
   */
  private JComponent myToolbar;
  private StatusBarImpl myStatusBar;
  /**
   * Current <code>ToolWindowsPane</code>. If there is no such pane then this field is null.
   */
  private ToolWindowsPane myToolWindowsPane;

  private final MyUISettingsListenerImpl myUISettingsListener;
  private JPanel myContentPane;
  private ActionManager myActionManager;
  private UISettings myUISettings;

  IdeRootPane(ActionManager actionManager, UISettings uiSettings, DataManager dataManager, KeymapManager keymapManager){
    myActionManager = actionManager;
    myUISettings = uiSettings;

    updateToolbar();

    createStatusBar();
    updateStatusBarVisibility();
    myContentPane.add(myStatusBar,BorderLayout.SOUTH);

    myUISettingsListener=new MyUISettingsListenerImpl();
    setJMenuBar(new IdeMenuBar(myActionManager, dataManager, keymapManager));
  }

  /**
   * Invoked when enclosed frame is being shown.
   */
  public final void addNotify(){
    super.addNotify();
    myUISettings.addUISettingsListener(myUISettingsListener);
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  public final void removeNotify(){
    myUISettings.removeUISettingsListener(myUISettingsListener);
    super.removeNotify();
  }

  /**
   * Sets current tool windows pane (panel where all tool windows are located).
   * If <code>toolWindowsPane</code> is <code>null</code> then the method just removes
   * the current tool windows pane.
   */
  final void setToolWindowsPane(final ToolWindowsPane toolWindowsPane){
    final Container contentPane=getContentPane();
    if(myToolWindowsPane!=null){
      contentPane.remove(myToolWindowsPane);
    }
    myToolWindowsPane=toolWindowsPane;
    if(myToolWindowsPane!=null){
      contentPane.add(myToolWindowsPane,BorderLayout.CENTER);
    }
  }

  protected final Container createContentPane(){
    myContentPane = new JPanel(new BorderLayout());
    myContentPane.setBackground(Color.GRAY);

    return myContentPane;
  }

  void updateToolbar() {
    if (myToolbar != null) {
      myContentPane.remove(myToolbar);
    }
    myToolbar = createToolbar();
    myContentPane.add(myToolbar,BorderLayout.NORTH);
    updateToolbarVisibility();
    myContentPane.revalidate();
  }

  private JComponent createToolbar() {
    ActionGroup group = (ActionGroup)myActionManager.getAction(IdeActions.GROUP_MAIN_TOOLBAR);
    final ActionToolbarEx toolBar=(ActionToolbarEx)myActionManager.createActionToolbar(
      ActionPlaces.MAIN_TOOLBAR,
      group,
      true
    );
    toolBar.setLayoutPolicy(ActionToolbarEx.WRAP_LAYOUT_POLICY);
    return toolBar.getComponent();
  }

  private void createStatusBar() {
    myStatusBar = new StatusBarImpl(myActionManager, myUISettings);
  }

  final StatusBarEx getStatusBar() {
    return myStatusBar;
  }

  private void updateToolbarVisibility(){
    myToolbar.setVisible(myUISettings.SHOW_MAIN_TOOLBAR);
  }

  private void updateStatusBarVisibility(){
    myStatusBar.setVisible(myUISettings.SHOW_STATUS_BAR);
  }

  private final class MyUISettingsListenerImpl implements UISettingsListener{
    public final void uiSettingsChanged(final UISettings source){
      updateToolbarVisibility();
      updateStatusBarVisibility();
    }
  }
}