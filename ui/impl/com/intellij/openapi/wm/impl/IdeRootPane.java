package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.navigationToolbar.NavigationToolbarPanel;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreen;
import org.jetbrains.annotations.Nullable;

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

  private JPanel myNorthPanel = new JPanel(new BorderLayout());
  private NavigationToolbarPanel myNavigationBar;

  /**
   * Current <code>ToolWindowsPane</code>. If there is no such pane then this field is null.
   */
  private ToolWindowsPane myToolWindowsPane;

  private final MyUISettingsListenerImpl myUISettingsListener;
  private JPanel myContentPane;
  private ActionManager myActionManager;
  private UISettings myUISettings;
  private static Component myWelcomePane;

  IdeRootPane(ActionManager actionManager, UISettings uiSettings, DataManager dataManager, KeymapManager keymapManager){
    myActionManager = actionManager;
    myUISettings = uiSettings;

    updateToolbar();
    myContentPane.add(myNorthPanel, BorderLayout.NORTH);

    createStatusBar();
    updateStatusBarVisibility();

    myContentPane.add(myStatusBar, BorderLayout.SOUTH);

    myUISettingsListener=new MyUISettingsListenerImpl();
    setJMenuBar(new IdeMenuBar(myActionManager, dataManager, keymapManager));

    if (!GeneralSettings.getInstance().isReopenLastProject() ||
        RecentProjectsManager.getInstance().getLastProjectPath() == null) {
      myWelcomePane = WelcomeScreen.createWelcomePanel();
      myContentPane.add(myWelcomePane);
    }
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
  final void setToolWindowsPane(final ToolWindowsPane toolWindowsPane) {
    final JComponent contentPane = (JComponent)getContentPane();
    if(myToolWindowsPane != null){
      contentPane.remove(myToolWindowsPane);
    }

    if (myWelcomePane != null) {
      contentPane.remove(myWelcomePane);
      myWelcomePane = null;
    }

    myToolWindowsPane = toolWindowsPane;
    if(myToolWindowsPane != null) {
      contentPane.add(myToolWindowsPane,BorderLayout.CENTER);
    }
    else {
      myWelcomePane = WelcomeScreen.createWelcomePanel();
      contentPane.add(myWelcomePane);
    }

    contentPane.revalidate();
  }

  protected final Container createContentPane(){
    myContentPane = new JPanel(new BorderLayout());
    myContentPane.setBackground(Color.GRAY);

    return myContentPane;
  }

  void updateToolbar() {
    if (myToolbar != null) {
      myNorthPanel.remove(myToolbar);
    }
    myToolbar = createToolbar();
    myNorthPanel.add(myToolbar,BorderLayout.NORTH);
    updateToolbarVisibility();
    myContentPane.revalidate();
  }

  void updateMainMenuActions(){
    ((IdeMenuBar)menuBar).updateMenuActions();
    menuBar.repaint();
  }

  private JComponent createToolbar() {
    ActionGroup group = (ActionGroup)CustomizableActionsSchemas.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_TOOLBAR);
    final ActionToolbar toolBar= myActionManager.createActionToolbar(
      ActionPlaces.MAIN_TOOLBAR,
      group,
      true
    );
    toolBar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);
    return toolBar.getComponent();
  }

  private void createStatusBar() {
    myStatusBar = new StatusBarImpl(myUISettings);
  }

  @Nullable
  final StatusBarEx getStatusBar() {
    return myStatusBar;
  }

  public NavigationToolbarPanel getNavigationBar(){
    return myNavigationBar;
  }

  private void updateToolbarVisibility(){
    myToolbar.setVisible(myUISettings.SHOW_MAIN_TOOLBAR);
  }

  private void updateStatusBarVisibility(){
    myStatusBar.setVisible(myUISettings.SHOW_STATUS_BAR);
  }

  private void updateNavigationBarVisibility(){
    if (myNavigationBar != null) {
      if (myUISettings.SHOW_NAVIGATION_BAR){
        myNavigationBar.installListeners();
      } else {
        myNavigationBar.uninstallListeners();
      }
      myNavigationBar.setVisible(myUISettings.SHOW_NAVIGATION_BAR);
      myNavigationBar.updateState(myUISettings.SHOW_NAVIGATION_BAR);
    }
  }

  public void installNavigationBar(final Project project) {
    myNavigationBar = new NavigationToolbarPanel(project);
    myNavigationBar.registerSelectInTarget();
    myNorthPanel.add(myNavigationBar, BorderLayout.SOUTH);
    updateNavigationBarVisibility();
  }

  public void deinstallNavigationBar(){
    if (myNavigationBar != null) {
      myNavigationBar.uninstallListeners();
      myNorthPanel.remove(myNavigationBar);
      myNavigationBar = null;
    }
  }

  private final class MyUISettingsListenerImpl implements UISettingsListener{
    public final void uiSettingsChanged(final UISettings source){
      updateToolbarVisibility();
      updateStatusBarVisibility();
      updateNavigationBarVisibility();
    }
  }
}