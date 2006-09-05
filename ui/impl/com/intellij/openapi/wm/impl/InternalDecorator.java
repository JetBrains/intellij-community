package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.ex.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.WeakKeymapManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.ui.ActivatableLineBorder;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.UIBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;
import javax.swing.plaf.metal.MetalButtonUI;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
public final class InternalDecorator extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.InternalDecorator");

  private static final int DIVIDER_WIDTH = 5;

  /*
   * Icons for buttons in the title bar.
   */
  private static final Icon ourSlidingIcon = IconLoader.getIcon("/general/sliding.png");
  private static final Icon ourSlidingInactiveIcon = IconLoader.getIcon("/general/slidingInactive.png");
  private static final Icon ourDockedIcon = IconLoader.getIcon("/general/docked.png");
  private static final Icon ourDockedInactiveIcon = IconLoader.getIcon("/general/dockedInactive.png");

  private static final Icon ourAuthoHideOnIcon = IconLoader.getIcon("/general/autohideOn.png");
  private static final Icon ourAuthoHideOnInactiveIcon = IconLoader.getIcon("/general/autohideOnInactive.png");
  private static final Icon ourAuthoHideOffIcon = IconLoader.getIcon("/general/autohideOff.png");
  private static final Icon ourAuthoHideOffInactiveIcon = IconLoader.getIcon("/general/autohideOffInactive.png");

  private static final Icon ourHideIcon = IconLoader.getIcon("/general/hideToolWindow.png");
  private static final Icon ourHideInactiveIcon = IconLoader.getIcon("/general/hideToolWindowInactive.png");

  private static final Icon ourFloatingIcon = IconLoader.getIcon("/general/floating.png");
  private static final Icon ourFloatingInactiveIcon = IconLoader.getIcon("/general/floatingInactive.png");
  private static final Icon ourFixIcon = IconLoader.getIcon("/general/fix.png");
  private static final Icon ourFixInactiveIcon = IconLoader.getIcon("/general/fixInactive.png");

  private Project myProject;
  private WindowInfo myInfo;
  private final ToolWindowImpl myToolWindow;
  private final MyDivider myDivider;
  private final TitlePanel myTitlePanel;
  private final ActivatableLineBorder myToolWindowBorder;
  private final MyTitleLabel myTitleLabel;
  private final MyTitleButton myToggleFloatingModeButton;
  private final Separator myFloatingDockSeparator;
  private final MyTitleButton myToggleDockModeButton;
  private final Separator myDockAutoHideSeparator;
  private final MyTitleButton myToggleAutoHideModeButton;
  private final Separator myAutoHideHideSeparator;
  private final MyTitleButton myHideButton;
  private final EventListenerList myListenerList;
  /*
   * Actions
   */
  private final TogglePinnedModeAction myToggleAutoHideModeAction;
  private final HideAction myHideAction;
  private final ToggleDockModeAction myToggleDockModeAction;
  private final ToggleFloatingModeAction myToggleFloatingModeAction;
  /**
   * Catches all event from tool window and modifies decorator's appearance.
   */
  private final ToolWindowHandler myToolWindowHandler;
  private final MyKeymapManagerListener myKeymapManagerListener;
  private final WeakKeymapManagerListener myWeakKeymapManagerListener;
  @NonNls protected static final String TOGGLE_PINNED_MODE_ACTION_ID = "TogglePinnedMode";
  @NonNls protected static final String TOGGLE_DOCK_MODE_ACTION_ID = "ToggleDockMode";
  @NonNls protected static final String TOGGLE_FLOATING_MODE_ACTION_ID = "ToggleFloatingMode";
  @NonNls protected static final String HIDE_ACTIVE_WINDOW_ACTION_ID = "HideActiveWindow";

  InternalDecorator(final Project project, final WindowInfo info, final ToolWindowImpl toolWindow) {
    super(new BorderLayout());
    myProject = project;
    myToolWindow = toolWindow;
    myDivider = new MyDivider();
    myTitlePanel = new TitlePanel();
    myTitleLabel = new MyTitleLabel();
    myToolWindowBorder = new ActivatableLineBorder();
    myToggleFloatingModeButton = new MyTitleButton();
    myToggleDockModeButton = new MyTitleButton();
    myToggleAutoHideModeButton = new MyTitleButton();
    myHideButton = new MyTitleButton();
    myListenerList = new EventListenerList();

    myToggleFloatingModeAction = new ToggleFloatingModeAction();
    myFloatingDockSeparator = new Separator();
    myFloatingDockSeparator.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
    myToggleDockModeAction = new ToggleDockModeAction();
    myDockAutoHideSeparator = new Separator();
    myToggleAutoHideModeAction = new TogglePinnedModeAction();
    myAutoHideHideSeparator = new Separator();
    myHideAction = new HideAction();

    myKeymapManagerListener = new MyKeymapManagerListener();
    final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
    myWeakKeymapManagerListener = new WeakKeymapManagerListener(keymapManager, myKeymapManagerListener);
    keymapManager.addKeymapManagerListener(myWeakKeymapManagerListener);

    init();

    myToolWindowHandler = new ToolWindowHandler();
    myToolWindow.addPropertyChangeListener(myToolWindowHandler);

    //

    apply(info);
  }

  /**
   * Applies specified decoration.
   */
  public final void apply(final WindowInfo info) {
    if (Comparing.equal(myInfo, info) || myProject.isDisposed()) {
      return;
    }
    myInfo = info;
    // Active
    final boolean active = info.isActive();
    myTitlePanel.setActive(active, !info.isSliding());

    //todo myToolWindowBorder.setActive(active);
    myFloatingDockSeparator.setActive(active);
    myDockAutoHideSeparator.setActive(active);
    myAutoHideHideSeparator.setActive(active);
    // Icon
    if (myInfo.isFloating()) {
      myTitleLabel.setIcon(myToolWindow.getIcon());
    }
    else {
      myTitleLabel.setIcon(null);
    }
    // Anchor
    final ToolWindowAnchor anchor = myInfo.getAnchor();
    if (info.isSliding()) {
      myDivider.invalidate();
      if (ToolWindowAnchor.TOP == anchor) {
        add(myDivider, BorderLayout.SOUTH);
      }
      else if (ToolWindowAnchor.LEFT == anchor) {
        add(myDivider, BorderLayout.EAST);
      }
      else if (ToolWindowAnchor.BOTTOM == anchor) {
        add(myDivider, BorderLayout.NORTH);
      }
      else if (ToolWindowAnchor.RIGHT == anchor) {
        add(myDivider, BorderLayout.WEST);
      }
      myDivider.setPreferredSize(new Dimension(DIVIDER_WIDTH, DIVIDER_WIDTH));
    }
    else { // docked and floating windows don't have divider
      remove(myDivider);
    }
    validate();
    repaint();
    // Type
    if (myInfo.isDocked()) {
      myToggleFloatingModeButton.setIcon(active ? ourFloatingIcon : ourFloatingInactiveIcon);
      myToggleDockModeButton.setVisible(true);
      myDockAutoHideSeparator.setVisible(true);
      myToggleDockModeButton.setIcon(active ? ourSlidingIcon : ourSlidingInactiveIcon);
      myToggleAutoHideModeButton.setVisible(true);
      myAutoHideHideSeparator.setVisible(true);
      myToggleAutoHideModeButton.setIcon(active ?
                                         (myInfo.isAutoHide() ? ourAuthoHideOnIcon : ourAuthoHideOffIcon) :
                                         (myInfo.isAutoHide() ? ourAuthoHideOnInactiveIcon : ourAuthoHideOffInactiveIcon));
      myHideButton.setVisible(true);
    }
    else if (myInfo.isFloating()) {
      myToggleFloatingModeButton.setIcon(active ? ourFixIcon : ourFixInactiveIcon);
      myToggleDockModeButton.setVisible(false);
      myDockAutoHideSeparator.setVisible(false);
      myToggleAutoHideModeButton.setVisible(true);
      myAutoHideHideSeparator.setVisible(true);
      myToggleAutoHideModeButton.setIcon(active ?
                                         myInfo.isAutoHide() ? ourAuthoHideOnIcon : ourAuthoHideOffIcon :
                                         myInfo.isAutoHide() ? ourAuthoHideOnInactiveIcon : ourAuthoHideOffInactiveIcon);
      myHideButton.setVisible(true);
    }
    else if (myInfo.isSliding()) {
      myToggleFloatingModeButton.setIcon(active ? ourFloatingIcon : ourFloatingInactiveIcon);
      myToggleDockModeButton.setVisible(true);
      myDockAutoHideSeparator.setVisible(true);
      myToggleDockModeButton.setIcon(active ? ourDockedIcon : ourDockedInactiveIcon);
      myToggleAutoHideModeButton.setVisible(false);
      myAutoHideHideSeparator.setVisible(false);
      myHideButton.setVisible(true);
    }
    myHideButton.setIcon(active ? ourHideIcon : ourHideInactiveIcon);
    //
    updateTitle();
    updateTooltips();

    // Push "apply" request forward

    if (myInfo.isFloating() && myInfo.isVisible()) {
      final FloatingDecorator floatingDecorator = (FloatingDecorator) SwingUtilities.getAncestorOfClass(FloatingDecorator.class, this);
      LOG.assertTrue(floatingDecorator != null);
      floatingDecorator.apply(myInfo);
    }
  }

  final void addInternalDecoratorListener(final InternalDecoratorListener l) {
    myListenerList.add(InternalDecoratorListener.class, l);
  }

  final void removeInternalDecoratorListener(final InternalDecoratorListener l) {
    myListenerList.remove(InternalDecoratorListener.class, l);
  }

  final void dispose() {
    removeAll();
    myToolWindow.removePropertyChangeListener(myToolWindowHandler);
    KeymapManagerEx.getInstanceEx().removeKeymapManagerListener(myWeakKeymapManagerListener);
    myProject = null;
  }

  private static String getToolTipTextByAction(@NonNls final String actionId, final String description) {
    String text = description;
    final String shortcutForAction = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(actionId));
    if (shortcutForAction.length() > 0) {
      text += "  " + shortcutForAction;
    }
    return text;
  }

  private void fireAnchorChanged(final ToolWindowAnchor anchor) {
    final InternalDecoratorListener[] listeners = (InternalDecoratorListener[]) myListenerList.getListeners(InternalDecoratorListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].anchorChanged(this, anchor);
    }
  }

  private void fireAutoHideChanged(final boolean autoHide) {
    final InternalDecoratorListener[] listeners = (InternalDecoratorListener[]) myListenerList.getListeners(InternalDecoratorListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].autoHideChanged(this, autoHide);
    }
  }

  /**
   * Fires event that "hide" button has been pressed.
   */
  final void fireHidden() {
    final InternalDecoratorListener[] listeners = (InternalDecoratorListener[]) myListenerList.getListeners(InternalDecoratorListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].hidden(this);
    }
  }

  /**
   * Fires event that user performed click into the title bar area.
   */
  final void fireActivated() {
    final InternalDecoratorListener[] listeners = (InternalDecoratorListener[]) myListenerList.getListeners(InternalDecoratorListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].activated(this);
    }
  }

  private void fireTypeChanged(final ToolWindowType type) {
    final InternalDecoratorListener[] listeners = (InternalDecoratorListener[]) myListenerList.getListeners(InternalDecoratorListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].typeChanged(this, type);
    }
  }

  final void fireResized() {
    final InternalDecoratorListener[] listeners = (InternalDecoratorListener[]) myListenerList.getListeners(InternalDecoratorListener.class);
    for (int i = 0; i < listeners.length; i++) {
      listeners[i].resized(this);
    }
  }

  private void init() {
    enableEvents(ComponentEvent.COMPONENT_EVENT_MASK);
    // Compose title bar
    final Component strut = Box.createHorizontalStrut(3);
    myTitlePanel.add(strut, BorderLayout.WEST);
    myTitleLabel.setForeground(Color.white);
    myTitlePanel.add(myTitleLabel, BorderLayout.CENTER);

    final JPanel buttonPanel = new JPanel(new GridBagLayout());
    buttonPanel.setOpaque(false);
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));
    buttonPanel.add(myToggleFloatingModeButton,
                    new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    buttonPanel.add(myFloatingDockSeparator,
                    new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    buttonPanel.add(myToggleDockModeButton,
                    new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    buttonPanel.add(myDockAutoHideSeparator,
                    new GridBagConstraints(3, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    buttonPanel.add(myToggleAutoHideModeButton,
                    new GridBagConstraints(4, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    buttonPanel.add(myAutoHideHideSeparator,
                    new GridBagConstraints(5, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    buttonPanel.add(myHideButton,
                    new GridBagConstraints(6, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));

    myTitlePanel.add(buttonPanel, BorderLayout.EAST);
    final JPanel contentPane = new JPanel(new BorderLayout());
    contentPane.setBorder(myToolWindowBorder);
    contentPane.add(myTitlePanel, BorderLayout.NORTH);
    JPanel innerPanel = new JPanel(new BorderLayout());
    innerPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    JComponent toolWindowComponent = myToolWindow.getComponent();
    innerPanel.add(toolWindowComponent, BorderLayout.CENTER);
    contentPane.add(innerPanel, BorderLayout.CENTER);
    add(contentPane, BorderLayout.CENTER);

    // Add listeners

    myToggleFloatingModeButton.addActionListener(myToggleFloatingModeAction);
    myToggleDockModeButton.addActionListener(myToggleDockModeAction);
    myToggleAutoHideModeButton.addActionListener(myToggleAutoHideModeAction);
    myHideButton.addActionListener(myHideAction);
    myTitleLabel.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final ActionGroup group = createPopupGroup();
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    });
    myTitleLabel.addMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (!e.isPopupTrigger()) {
          fireActivated();
        }
      }
    });

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        ToolWindowManager.getInstance(myProject).activateEditorComponent();
      }
    },
                           KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                           JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  final ActionGroup createPopupGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();

    if (myInfo.isDocked()) {
      group.add(myToggleAutoHideModeAction);
      group.add(myToggleDockModeAction);
      group.add(myToggleFloatingModeAction);
    }
    else if (myInfo.isFloating()) {
      group.add(myToggleAutoHideModeAction);
      group.add(myToggleFloatingModeAction);
    }
    else if (myInfo.isSliding()) {
      group.add(myToggleDockModeAction);
      group.add(myToggleFloatingModeAction);
    }

    final DefaultActionGroup moveGroup = new DefaultActionGroup(UIBundle.message("tool.window.move.to.action.group.name"), true);
    final ToolWindowAnchor anchor = myInfo.getAnchor();
    if (anchor != ToolWindowAnchor.TOP) {
      final AnAction topAction = new ChangeAnchorAction(UIBundle.message("tool.window.move.to.top.action.name"), ToolWindowAnchor.TOP);
      moveGroup.add(topAction);
    }
    if (anchor != ToolWindowAnchor.LEFT) {
      final AnAction leftAction = new ChangeAnchorAction(UIBundle.message("tool.window.move.to.left.action.name"), ToolWindowAnchor.LEFT);
      moveGroup.add(leftAction);
    }
    if (anchor != ToolWindowAnchor.BOTTOM) {
      final AnAction bottomAction = new ChangeAnchorAction(UIBundle.message("tool.window.move.to.bottom.action.name"), ToolWindowAnchor.BOTTOM);
      moveGroup.add(bottomAction);
    }
    if (anchor != ToolWindowAnchor.RIGHT) {
      final AnAction rightAction = new ChangeAnchorAction(UIBundle.message("tool.window.move.to.right.action.name"), ToolWindowAnchor.RIGHT);
      moveGroup.add(rightAction);
    }
    group.add(moveGroup);

    group.addSeparator();
    group.add(myHideAction);
    return group;
  }

  /**
   * @return tool window associated with the decorator.
   */
  final ToolWindowImpl getToolWindow() {
    return myToolWindow;
  }

  /**
   * @return last window info applied to the decorator.
   */
  final WindowInfo getWindowInfo() {
    return myInfo;
  }

  protected final void processComponentEvent(final ComponentEvent e) {
    super.processComponentEvent(e);
    if (ComponentEvent.COMPONENT_RESIZED == e.getID()) {
      fireResized();
    }
  }

  private void updateTitle() {
    final StringBuffer fullTitle = new StringBuffer();
    //  Due to JDK's bug #4234645 we cannot support custom decoration on Linux platform.
    // The prblem is that Window.setLocation() doesn't work properly wjen the dialod is displayable.
    // Therefore we use native WM decoration. When the dialog has native decoration we show window ID
    // in the dialog's title and window title at the custom title panel. If the custom decoration
    // is used we show composite string at the custom title panel.
    // TODO[vova] investigate the problem under Mac OSX.
    if (SystemInfo.isWindows || !myInfo.isFloating()) {
      fullTitle.append(myInfo.getId());
      final String title = myToolWindow.getTitle();
      if (title != null && title.length() > 0) {
        fullTitle.append(" - ").append(title);
      }
    }
    else { // Unixes ans MacOSX go here when tool window is in floating mode
      final String title = myToolWindow.getTitle();
      if (title != null && title.length() > 0) {
        fullTitle.append(title);
      }
    }
    myTitleLabel.setText(fullTitle.toString());
  }

  private void updateTooltips() {
    if (myInfo.isDocked()) {
      myToggleDockModeButton.setToolTipText(getToolTipTextByAction(TOGGLE_DOCK_MODE_ACTION_ID,
                                                                   UIBundle.message("tool.wondow.undock.action.name")));
    }
    else if (myInfo.isSliding()) {
      myToggleDockModeButton.setToolTipText(getToolTipTextByAction(TOGGLE_DOCK_MODE_ACTION_ID,
                                                                   UIBundle.message("tool.wondow.dock.action.name")));
    }
    myToggleFloatingModeButton.setToolTipText(getToolTipTextByAction(TOGGLE_FLOATING_MODE_ACTION_ID, myInfo.isFloating() ? UIBundle
      .message("tool.wondow.fix.action.name") : UIBundle.message("tool.wondow.float.action.name")));
    myToggleAutoHideModeButton.setToolTipText(getToolTipTextByAction(TOGGLE_PINNED_MODE_ACTION_ID, myInfo.isAutoHide() ? UIBundle
      .message("tool.wondow.pin.action.name") : UIBundle.message("tool.wondow.unpin.action.name")));
    myHideButton.setToolTipText(getToolTipTextByAction(HIDE_ACTIVE_WINDOW_ACTION_ID, UIBundle.message("tool.window.hide.action.name")));
  }

  private final class ChangeAnchorAction extends AnAction {
    private final ToolWindowAnchor myAnchor;

    public ChangeAnchorAction(final String title, final ToolWindowAnchor anchor) {
      super(title);
      myAnchor = anchor;
    }

    public final void actionPerformed(final AnActionEvent e) {
      fireAnchorChanged(myAnchor);
    }
  }

  private final class HideAction extends AnAction {
    @NonNls public static final String HIDE_ACTIVE_WINDOW_ACTION_ID = InternalDecorator.HIDE_ACTIVE_WINDOW_ACTION_ID;

    public HideAction() {
      copyFrom(ActionManager.getInstance().getAction(HIDE_ACTIVE_WINDOW_ACTION_ID));
      getTemplatePresentation().setText(UIBundle.message("tool.window.hide.action.name"));
    }

    public final void actionPerformed(final AnActionEvent e) {
      fireHidden();
    }

    public final void update(final AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(myInfo.isVisible());
    }
  }

  private final class TogglePinnedModeAction extends ToggleAction {
    public TogglePinnedModeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_PINNED_MODE_ACTION_ID));
    }

    public final boolean isSelected(final AnActionEvent event) {
      return !myInfo.isAutoHide();
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      fireAutoHideChanged(!myInfo.isAutoHide());
    }
  }

  private final class ToggleDockModeAction extends ToggleAction {
    public ToggleDockModeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_DOCK_MODE_ACTION_ID));
    }

    public final boolean isSelected(final AnActionEvent event) {
      return myInfo.isDocked();
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      if (myInfo.isDocked()) {
        fireTypeChanged(ToolWindowType.SLIDING);
      }
      else if (myInfo.isSliding()) {
        fireTypeChanged(ToolWindowType.DOCKED);
      }
    }
  }

  private final class ToggleFloatingModeAction extends ToggleAction {
    public ToggleFloatingModeAction() {
      copyFrom(ActionManager.getInstance().getAction(TOGGLE_FLOATING_MODE_ACTION_ID));
    }

    public final boolean isSelected(final AnActionEvent event) {
      return myInfo.isFloating();
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      if (myInfo.isFloating()) {
        fireTypeChanged(myInfo.getInternalType());
      }
      else {
        fireTypeChanged(ToolWindowType.FLOATING);
      }
    }
  }

  private final class MyDivider extends JPanel {
    private boolean myDragging;
    private Point myLastPoint;

    public MyDivider() {
      myDragging = false;
      enableEvents(MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
      setBorder(new DividerBorder());
    }

    protected final void processMouseMotionEvent(final MouseEvent e) {
      super.processMouseMotionEvent(e);
      if (MouseEvent.MOUSE_DRAGGED == e.getID()) {
        myDragging = true;
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        final boolean isVertical = anchor == ToolWindowAnchor.TOP || anchor == ToolWindowAnchor.BOTTOM;
        setCursor(isVertical ? Cursor.getPredefinedCursor(9) : Cursor.getPredefinedCursor(11));
        final Point point = e.getPoint();

        final Container windowPane = InternalDecorator.this.getParent();
        myLastPoint = SwingUtilities.convertPoint(this, point, windowPane);
        myLastPoint.x = Math.min(Math.max(myLastPoint.x, 0), windowPane.getWidth());
        myLastPoint.y = Math.min(Math.max(myLastPoint.y, 0), windowPane.getHeight());

        final Rectangle bounds = InternalDecorator.this.getBounds();
        if (anchor == ToolWindowAnchor.TOP) {
          if (myLastPoint.y < DIVIDER_WIDTH) {
            myLastPoint.y = DIVIDER_WIDTH;
          }
          InternalDecorator.this.setBounds(0, 0, bounds.width, myLastPoint.y);
        }
        else if (anchor == ToolWindowAnchor.LEFT) {
          if (myLastPoint.x < DIVIDER_WIDTH) {
            myLastPoint.x = DIVIDER_WIDTH;
          }
          InternalDecorator.this.setBounds(0, 0, myLastPoint.x, bounds.height);
        }
        else if (anchor == ToolWindowAnchor.BOTTOM) {
          if (myLastPoint.y > windowPane.getHeight() - DIVIDER_WIDTH) {
            myLastPoint.y = windowPane.getHeight() - DIVIDER_WIDTH;
          }
          InternalDecorator.this.setBounds(0, myLastPoint.y, bounds.width, windowPane.getHeight() - myLastPoint.y);
        }
        else if (anchor == ToolWindowAnchor.RIGHT) {
          if (myLastPoint.x > windowPane.getWidth() - DIVIDER_WIDTH) {
            myLastPoint.x = windowPane.getWidth() - DIVIDER_WIDTH;
          }
          InternalDecorator.this.setBounds(myLastPoint.x, 0, windowPane.getWidth() - myLastPoint.x, bounds.height);
        }
        InternalDecorator.this.validate();
      }
    }

    protected final void processMouseEvent(final MouseEvent e) {
      super.processMouseEvent(e);
      final ToolWindowAnchor anchor = myInfo.getAnchor();
      final boolean isVerticalCursor = anchor == ToolWindowAnchor.TOP || anchor == ToolWindowAnchor.BOTTOM;
      switch (e.getID()) {
        case MouseEvent.MOUSE_MOVED:
        default:
          break;
        case MouseEvent.MOUSE_ENTERED:
          setCursor(isVerticalCursor
                    ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
                    : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
          break;
        case MouseEvent.MOUSE_EXITED:
          if (!myDragging) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          }
          break;
        case MouseEvent.MOUSE_PRESSED:
          setCursor(isVerticalCursor
                    ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)
                    : Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
          break;
        case MouseEvent.MOUSE_RELEASED:
          myDragging = false;
          myLastPoint = null;
          break;
        case MouseEvent.MOUSE_CLICKED:
          break;
      }
    }

    private final class DividerBorder implements Border {
      public final void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
        final ToolWindowAnchor anchor = myInfo.getAnchor();
        final boolean isVertical = anchor == ToolWindowAnchor.TOP || anchor == ToolWindowAnchor.BOTTOM;
        if (isVertical) {
          if (anchor == ToolWindowAnchor.TOP) {
            g.setColor(Color.white);
            UIUtil.drawLine(g, x, y, x + width - 1, y);
            g.setColor(Color.darkGray);
            UIUtil.drawLine(g, x, y + height - 1, x + width - 1, y + height - 1);
          }
          else {
            g.setColor(Color.darkGray);
            UIUtil.drawLine(g, x, y, x + width - 1, y);
            g.setColor(Color.white);
            UIUtil.drawLine(g, x, y + height - 1, x + width - 1, y + height - 1);
          }
        }
        else {
          if (anchor == ToolWindowAnchor.LEFT) {
            g.setColor(Color.white);
            UIUtil.drawLine(g, x, y, x, y + height - 1);
            g.setColor(Color.darkGray);
            UIUtil.drawLine(g, x + width - 1, y, x + width - 1, y + height - 1);
          }
          else {
            g.setColor(Color.darkGray);
            UIUtil.drawLine(g, x, y, x, y + height - 1);
            g.setColor(Color.white);
            UIUtil.drawLine(g, x + width - 1, y, x + width - 1, y + height - 1);
          }
        }
      }

      public final Insets getBorderInsets(final Component c) {
        if (c instanceof MyDivider) {
          return new Insets(1, 1, 1, 1);
        }
        return new Insets(0, 0, 0, 0);
      }

      public final boolean isBorderOpaque() {
        return true;
      }
    }
  }


  private final class MyTitleLabel extends JLabel {
    private Point myLastPoint;

    public MyTitleLabel() {
      enableEvents(MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
    }

    public void updateUI() {
      super.updateUI();
      setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
    }

    protected final void processMouseEvent(final MouseEvent e) {
      super.processMouseEvent(e);
      switch (e.getID()) {
        case MouseEvent.MOUSE_PRESSED:
          {
            myLastPoint = e.getPoint();
            SwingUtilities.convertPointToScreen(myLastPoint, this);
            break;
          }
      }
    }

    protected final void processMouseMotionEvent(final MouseEvent e) {
      super.processMouseMotionEvent(e);
      switch (e.getID()) {
        case MouseEvent.MOUSE_DRAGGED:
          {
            // 1. myLast point can be null due to bugs in Swing.
            // 2. do not allow drag enclosed window if ToolWindow isn't floating
            if (myLastPoint == null || !myInfo.isFloating()) {
              return;
            }
            final Window window = SwingUtilities.windowForComponent(this);
            final Rectangle oldBounds = window.getBounds();
            final Point newPoint = e.getPoint();
            SwingUtilities.convertPointToScreen(newPoint, this);
            final Point offset = new Point(newPoint.x - myLastPoint.x, newPoint.y - myLastPoint.y);
            window.setLocation(oldBounds.x + offset.x, oldBounds.y + offset.y);
            myLastPoint = newPoint;
            break;
          }
      }
    }
  }

  /**
   * Updates tooltips.
   */
  private final class MyKeymapManagerListener implements KeymapManagerListener {
    public final void activeKeymapChanged(final Keymap keymap) {
      updateTooltips();
    }
  }

  private static final class MyTitleButton extends FixedSizeButton {
    public MyTitleButton() {
      super(16);
      setBorder(BorderFactory.createEmptyBorder());
    }

    public final void addActionListener(final AnAction action) {
      final DataContext dataContext = DataManager.getInstance().getDataContext(this);

      final ActionListener actionListener = new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
          actionManager.fireBeforeActionPerformed(action, dataContext);
          final Component component = ((Component) dataContext.getData(DataConstantsEx.CONTEXT_COMPONENT));
          if (component != null && !component.isShowing()) {
            return;
          }
          action.actionPerformed(new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, action.getTemplatePresentation(),
                                                   ActionManager.getInstance(),
                                                   0));
        }
      };

      addActionListener(actionListener);
    }

    /**
     * Some UIs paint only opague buttons. It causes that button has gray background color.
     * To prevent this I don't allow to change UI.
     */
    public final void updateUI() {
      setUI(new MyTitleButtonUI());
      setOpaque(false);
      setRolloverEnabled(true);
      setContentAreaFilled(false);
    }
  }

  private static final class MyTitleButtonUI extends MetalButtonUI {
    @Override protected void paintIcon(Graphics g, JComponent c, Rectangle iconRect) {
      MyTitleButton btn = (MyTitleButton)c;
      if (btn.getModel().isArmed() && btn.getModel().isPressed()) {
        iconRect = new Rectangle(iconRect.x - 1, iconRect.y, iconRect.width, iconRect.height);
      }
      super.paintIcon(g, c, iconRect);
    }
  }

  private static final class Separator extends JLabel {
    private static final Icon ourActiveSeparator = IconLoader.getIcon("/general/separator.png");
    private static final Icon ourInactiveSeparator = IconLoader.getIcon("/general/inactiveSeparator.png");

    public final void setActive(final boolean active) {
      setIcon(active ? ourActiveSeparator : ourInactiveSeparator);
    }

    public final void updateUI() {
      super.updateUI();
      setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
    }
  }

  /**
   * Synchronizes decorator with IdeToolWindow changes.
   */
  private final class ToolWindowHandler implements PropertyChangeListener {
    public final void propertyChange(final PropertyChangeEvent e) {
      final String name = e.getPropertyName();
      if (ToolWindowEx.PROP_TITLE.equals(name)) {
        updateTitle();
      }
    }
  }
}