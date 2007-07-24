package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.navigationToolbar.NavBarPanel;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomizableActionsSchemas;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreen;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;

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

  private JPanel myNorthPanel = new JPanel(new GridBagLayout());
  private NavBarPanel myNavigationBar;
  private JLabel myCloseNavBarLabel;

  /**
   * Current <code>ToolWindowsPane</code>. If there is no such pane then this field is null.
   */
  private ToolWindowsPane myToolWindowsPane;
  private final MyUISettingsListenerImpl myUISettingsListener;
  private JPanel myContentPane;
  private ActionManager myActionManager;
  private UISettings myUISettings;

  private static Component myWelcomePane;
  private boolean myGlassPaneInitialized;
  private IdeGlassPaneImpl myGlassPane;

  private static final Icon CROSS_ICON = IconLoader.getIcon("/actions/cross.png");
  private Application myApplication;

  IdeRootPane(ActionManager actionManager, UISettings uiSettings, DataManager dataManager, KeymapManager keymapManager,
              final Application application){
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

    myGlassPane = new IdeGlassPaneImpl(this);
    setGlassPane(myGlassPane);
    myGlassPaneInitialized = true;

    myGlassPane.setVisible(false);
    myApplication = application;
  }


  public void setGlassPane(final Component glass) {
    if (myGlassPaneInitialized) throw new IllegalStateException("Setting of glass pane for IdeFrame is prohibited");
    super.setGlassPane(glass);
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
    else if (!myApplication.isDisposeInProgress()) {
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
    myNorthPanel.add(myToolbar, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0,0));
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

  public NavBarPanel getNavigationBar(){
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
      myCloseNavBarLabel.setVisible(myUISettings.SHOW_NAVIGATION_BAR);
      myNavigationBar.updateState(myUISettings.SHOW_NAVIGATION_BAR);
    }
  }

  public void installNavigationBar(final Project project) {
    myNavigationBar = new NavBarPanel(project);
    final int iconWidth = CROSS_ICON.getIconWidth();
    final int iconHeight = CROSS_ICON.getIconHeight();
    myNavigationBar.cutBorder(2 * iconWidth);
    myNorthPanel.add(myNavigationBar, new GridBagConstraints(0, 1, 2, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                                             new Insets(0, 0, 0, 0), 0, 0));
    myCloseNavBarLabel = new JLabel(new Icon() {
      public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
        final GraphicsConfig config = new GraphicsConfig(g);
        config.setAntialiasing(true);

        Graphics2D g2d = (Graphics2D)g;

        final GeneralPath path = new GeneralPath();

        path.moveTo( 0, iconHeight);
        path.curveTo(2 * iconWidth/3, 2 * iconHeight/3, iconWidth/3, iconHeight/3, iconWidth, 0);
        path.lineTo(2 * iconWidth - 2, 0);
        path.lineTo(2 * iconWidth - 2, iconHeight);
        path.lineTo(0, iconHeight);
        path.closePath();

        g2d.setPaint(UIUtil.getListBackground());
        g2d.fill(path);

        g2d.setPaint(myCloseNavBarLabel.getBackground().darker());
        g2d.draw(path);

        CROSS_ICON.paintIcon(c, g, x + iconWidth - 2, y + 1);

        config.restore();
      }

      public int getIconWidth() {
        return 2 * iconWidth;
      }

      public int getIconHeight() {
        return iconHeight;
      }
    });
    myCloseNavBarLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        myUISettings.SHOW_NAVIGATION_BAR = false;
        updateNavigationBarVisibility();
      }
    });
    myNorthPanel.add(myCloseNavBarLabel, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.SOUTHEAST, GridBagConstraints.NONE,
                                                                new Insets(0, 0, 0, 0), 0, 0));
    updateNavigationBarVisibility();
  }

  public void deinstallNavigationBar(){
    if (myNavigationBar != null) {
      myNavigationBar.uninstallListeners();
      myNorthPanel.remove(myNavigationBar);
      myNorthPanel.remove(myCloseNavBarLabel);
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

  public boolean isOptimizedDrawingEnabled() {
    return !myGlassPane.hasPainters();
  }
}