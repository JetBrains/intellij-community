/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.AnchoredButton;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.openapi.wm.impl.StripeButtonUI;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * @author Alexander Lobas
 */
public class LightToolWindow extends JPanel {
  public static final String LEFT_MIN_KEY = "left";
  public static final String RIGHT_MIN_KEY = "right";
  private static final String IGNORE_WIDTH_KEY = "ignore_width";

  private final LightToolWindowContent myContent;
  private final JComponent myFocusedComponent;
  private final ThreeComponentsSplitter myContentSplitter;
  private ToolWindowAnchor myAnchor;
  private final Project myProject;
  private final AbstractToolWindowManager myManager;
  private final PropertiesComponent myPropertiesComponent;
  private boolean myShowContent;
  private final String myShowStateKey;
  private int myCurrentWidth;
  private final String myWidthKey;
  private final JPanel myMinimizeComponent;
  private final AnchoredButton myMinimizeButton;

  private final TogglePinnedModeAction myToggleAutoHideModeAction = new TogglePinnedModeAction();
  private final ToggleDockModeAction myToggleDockModeAction = new ToggleDockModeAction();
  private final ToggleFloatingModeAction myToggleFloatingModeAction = new ToggleFloatingModeAction();
  private final ToggleSideModeAction myToggleSideModeAction = new ToggleSideModeAction();

  private final ComponentListener myWidthListener = new ComponentAdapter() {
    @Override
    public void componentResized(ComponentEvent e) {
      int width = isLeft() ? myContentSplitter.getFirstSize() : myContentSplitter.getLastSize();
      if (width > 0 && width != myCurrentWidth && myContentSplitter.getInnerComponent().getClientProperty(IGNORE_WIDTH_KEY) == null) {
        myCurrentWidth = width;
        myPropertiesComponent.setValue(myWidthKey, Integer.toString(width));
      }
    }
  };

  public LightToolWindow(LightToolWindowContent content,
                         String title,
                         Icon icon,
                         JComponent component,
                         JComponent focusedComponent,
                         ThreeComponentsSplitter contentSplitter,
                         ToolWindowAnchor anchor,
                         AbstractToolWindowManager manager,
                         Project project,
                         PropertiesComponent propertiesComponent,
                         String key,
                         int defaultWidth,
                         AnAction[] actions) {
    super(new BorderLayout());
    myContent = content;
    myFocusedComponent = focusedComponent;
    myContentSplitter = contentSplitter;
    myAnchor = anchor;
    myProject = project;
    myManager = manager;
    myPropertiesComponent = propertiesComponent;

    myShowStateKey = AbstractToolWindowManager.EDITOR_MODE + key + ".SHOW";
    myWidthKey = AbstractToolWindowManager.EDITOR_MODE + key + ".WIDTH";

    HeaderPanel header = new HeaderPanel();
    header.setLayout(new BorderLayout());
    add(header, BorderLayout.NORTH);

    JLabel titleLabel = new JLabel(title);
    titleLabel.setBorder(IdeBorderFactory.createEmptyBorder(2, 5, 2, 10));
    titleLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
    header.add(titleLabel, BorderLayout.CENTER);

    JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
    actionPanel.setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 2, 0));
    actionPanel.setOpaque(false);
    header.add(actionPanel, BorderLayout.EAST);

    if (actions != null) {
      for (AnAction action : actions) {
        addAction(actionPanel, action);
      }

      actionPanel.add(new JLabel(AllIcons.General.Divider));
    }

    addAction(actionPanel, new GearAction());
    addAction(actionPanel, new HideAction());

    JPanel contentWrapper = new JPanel(new BorderLayout());
    contentWrapper.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    contentWrapper.add(component, BorderLayout.CENTER);

    add(contentWrapper, BorderLayout.CENTER);

    addMouseListener(new MouseAdapter() {
      public void mouseReleased(final MouseEvent e) {
        IdeFocusManager.getInstance(myProject).requestFocus(myFocusedComponent, true);
      }
    });

    addMouseListener(new PopupHandler() {
      public void invokePopup(Component component, int x, int y) {
        showGearPopup(component, x, y);
      }
    });

    myMinimizeButton = new AnchoredButton(title, icon) {
      @Override
      public void updateUI() {
        setUI(StripeButtonUI.createUI(this));
        setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      }

      @Override
      public int getMnemonic2() {
        return 0;
      }

      @Override
      public ToolWindowAnchor getAnchor() {
        return myAnchor;
      }
    };
    myMinimizeButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myMinimizeButton.setSelected(false);
        updateContent(true, true);
      }
    });
    myMinimizeButton.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
    myMinimizeButton.setFocusable(false);

    myMinimizeButton.setRolloverEnabled(true);
    myMinimizeButton.setOpaque(false);

    myMinimizeComponent = new JPanel() {
      @Override
      public void doLayout() {
        Dimension size = myMinimizeButton.getPreferredSize();
        myMinimizeButton.setBounds(0, 0, getWidth(), size.height);
      }
    };
    myMinimizeComponent.add(myMinimizeButton);

    configureBorder();
    configureWidth(defaultWidth);
    updateContent(myPropertiesComponent.getBoolean(myShowStateKey, true), false);
  }

  private void configureBorder() {
    int borderStyle = isLeft() ? SideBorder.RIGHT : SideBorder.LEFT;
    setBorder(IdeBorderFactory.createBorder(borderStyle | SideBorder.BOTTOM));
    myMinimizeComponent.setBorder(IdeBorderFactory.createBorder(borderStyle));
  }

  private void configureWidth(int defaultWidth) {
    myCurrentWidth = myPropertiesComponent.getOrInitInt(myWidthKey, defaultWidth);
    updateWidth();
    myContentSplitter.getInnerComponent().addComponentListener(myWidthListener);
  }

  private void updateWidth() {
    if (isLeft()) {
      myContentSplitter.setFirstSize(myCurrentWidth);
    }
    else {
      myContentSplitter.setLastSize(myCurrentWidth);
    }
  }

  public void updateAnchor(ToolWindowAnchor newAnchor) {
    JComponent minimizeParent = myContentSplitter.getInnerComponent();
    minimizeParent.putClientProperty(IGNORE_WIDTH_KEY, Boolean.TRUE);

    if (myShowContent) {
      Object oldWindow = isLeft() ? myContentSplitter.getFirstComponent() : myContentSplitter.getLastComponent();
      if (oldWindow == this) {
        setContentComponent(null);
      }
    }
    else {
      String key = getMinKey();
      if (minimizeParent.getClientProperty(key) == myMinimizeComponent) {
        minimizeParent.putClientProperty(key, null);
      }
      minimizeParent.putClientProperty(isLeft() ? RIGHT_MIN_KEY : LEFT_MIN_KEY, myMinimizeComponent);
      minimizeParent.revalidate();
    }

    myAnchor = newAnchor;
    configureBorder();
    updateWidth();

    if (myShowContent) {
      setContentComponent(this);
    }

    minimizeParent.putClientProperty(IGNORE_WIDTH_KEY, null);
  }

  private void updateContent(boolean show, boolean flag) {
    myShowContent = show;

    String key = getMinKey();

    JComponent minimizeParent = myContentSplitter.getInnerComponent();

    if (show) {
      minimizeParent.putClientProperty(key, null);
      minimizeParent.remove(myMinimizeComponent);
    }

    setContentComponent(show ? this : null);

    if (!show) {
      minimizeParent.putClientProperty(key, myMinimizeComponent);
      minimizeParent.add(myMinimizeComponent);
    }

    minimizeParent.revalidate();

    if (flag) {
      myPropertiesComponent.setValue(myShowStateKey, Boolean.toString(show));
    }
  }

  private void setContentComponent(JComponent component) {
    if (isLeft()) {
      myContentSplitter.setFirstComponent(component);
    }
    else {
      myContentSplitter.setLastComponent(component);
    }
  }

  public void dispose() {
    JComponent minimizeParent = myContentSplitter.getInnerComponent();
    minimizeParent.removeComponentListener(myWidthListener);

    setContentComponent(null);
    myContent.dispose();

    if (!myShowContent) {
      minimizeParent.putClientProperty(getMinKey(), null);
      minimizeParent.remove(myMinimizeComponent);
      minimizeParent.revalidate();
    }
  }

  private String getMinKey() {
    return isLeft() ? LEFT_MIN_KEY : RIGHT_MIN_KEY;
  }

  public Object getContent() {
    return myContent;
  }

  private boolean isLeft() {
    return myAnchor == ToolWindowAnchor.LEFT;
  }

  private boolean isActive() {
    IdeFocusManager fm = IdeFocusManager.getInstance(myProject);
    Component component = fm.getFocusedDescendantFor(this);
    if (component != null) {
      return true;
    }
    Component owner = fm.getLastFocusedFor(WindowManager.getInstance().getIdeFrame(myProject));
    return owner != null && SwingUtilities.isDescendingFrom(owner, this);
  }

  private void addAction(JPanel actionPanel, AnAction action) {
    actionPanel.add(new ActionButton(action));
  }

  private DefaultActionGroup createGearPopupGroup() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(myManager.createGearActions());
    group.addSeparator();

    ToolWindowType type = myManager.getToolWindow().getType();
    if (type == ToolWindowType.DOCKED) {
      group.add(myToggleAutoHideModeAction);
      group.add(myToggleDockModeAction);
      group.add(myToggleFloatingModeAction);
      group.add(myToggleSideModeAction);
    }
    else if (type == ToolWindowType.FLOATING) {
      group.add(myToggleAutoHideModeAction);
      group.add(myToggleFloatingModeAction);
    }
    else if (type == ToolWindowType.SLIDING) {
      group.add(myToggleDockModeAction);
      group.add(myToggleFloatingModeAction);
    }

    return group;
  }

  private void showGearPopup(Component component, int x, int y) {
    ActionPopupMenu popupMenu =
      ((ActionManagerImpl)ActionManager.getInstance())
        .createActionPopupMenu(ToolWindowContentUi.POPUP_PLACE, createGearPopupGroup(), new MenuItemPresentationFactory(true));
    popupMenu.getComponent().show(component, x, y);
  }

  private class GearAction extends AnAction {
    public GearAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setIcon(AllIcons.General.Gear);
      presentation.setHoveredIcon(AllIcons.General.GearHover);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      int x = 0;
      int y = 0;
      InputEvent inputEvent = e.getInputEvent();
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }

      showGearPopup(inputEvent.getComponent(), x, y);
    }
  }

  private class HideAction extends AnAction {
    public HideAction() {
      Presentation presentation = getTemplatePresentation();
      presentation.setText(UIBundle.message("tool.window.hide.action.name"));
      if (isLeft()) {
        presentation.setIcon(AllIcons.General.HideLeftPart);
        presentation.setHoveredIcon(AllIcons.General.HideLeftPartHover);
      }
      else {
        presentation.setIcon(AllIcons.General.HideRightPart);
        presentation.setHoveredIcon(AllIcons.General.HideRightPartHover);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      updateContent(false, true);
    }
  }

  private class TogglePinnedModeAction extends ToggleAction {
    public TogglePinnedModeAction() {
      copyFrom(ActionManager.getInstance().getAction(InternalDecorator.TOGGLE_PINNED_MODE_ACTION_ID));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return !myManager.getToolWindow().isAutoHide();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      ToolWindow window = myManager.getToolWindow();
      window.setAutoHide(!window.isAutoHide());
      myManager.setEditorMode(null);
    }
  }

  private class ToggleDockModeAction extends ToggleAction {
    public ToggleDockModeAction() {
      copyFrom(ActionManager.getInstance().getAction(InternalDecorator.TOGGLE_DOCK_MODE_ACTION_ID));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myManager.getToolWindow().getType() == ToolWindowType.DOCKED;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      ToolWindow window = myManager.getToolWindow();
      ToolWindowType type = window.getType();
      if (type == ToolWindowType.DOCKED) {
        window.setType(ToolWindowType.SLIDING, null);
      }
      else if (type == ToolWindowType.SLIDING) {
        window.setType(ToolWindowType.DOCKED, null);
      }
      myManager.setEditorMode(null);
    }
  }

  private class ToggleFloatingModeAction extends ToggleAction {
    public ToggleFloatingModeAction() {
      copyFrom(ActionManager.getInstance().getAction(InternalDecorator.TOGGLE_FLOATING_MODE_ACTION_ID));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myManager.getToolWindow().getType() == ToolWindowType.FLOATING;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      ToolWindow window = myManager.getToolWindow();
      ToolWindowType type = window.getType();
      if (type == ToolWindowType.FLOATING) {
        window.setType(((ToolWindowEx)window).getInternalType(), null);
      }
      else {
        window.setType(ToolWindowType.FLOATING, null);
      }
      myManager.setEditorMode(null);
    }
  }

  private class ToggleSideModeAction extends ToggleAction {
    public ToggleSideModeAction() {
      copyFrom(ActionManager.getInstance().getAction(InternalDecorator.TOGGLE_SIDE_MODE_ACTION_ID));
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myManager.getToolWindow().isSplitMode();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myManager.getToolWindow().setSplitMode(state, null);
      myManager.setEditorMode(null);
    }
  }

  private class ActionButton extends Wrapper implements ActionListener {
    private final AnAction myAction;

    public ActionButton(AnAction action) {
      myAction = action;

      Presentation presentation = action.getTemplatePresentation();
      InplaceButton button = new InplaceButton(KeymapUtil.createTooltipText(presentation.getText(), action), EmptyIcon.ICON_16, this) {
        @Override
        public boolean isActive() {
          return LightToolWindow.this.isActive();
        }
      };
      button.setHoveringEnabled(!SystemInfo.isMac);
      setContent(button);

      Icon icon = presentation.getIcon();
      Icon hoveredIcon = presentation.getHoveredIcon();
      button.setIcons(icon, icon, hoveredIcon == null ? icon : hoveredIcon);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      InputEvent inputEvent = e.getSource() instanceof InputEvent ? (InputEvent)e.getSource() : null;
      myAction.actionPerformed(AnActionEvent.createFromInputEvent(myAction, inputEvent, ActionPlaces.UNKNOWN));
    }
  }

  private class HeaderPanel extends JPanel {
    private BufferedImage myActiveImage;
    private BufferedImage myImage;

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(size.width, TabsUtil.getTabsHeight());
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension size = super.getMinimumSize();
      return new Dimension(size.width, TabsUtil.getTabsHeight());
    }

    protected void _paintComponent(Graphics g) { // XXX: visual artifacts on linux
      Rectangle r = getBounds();

      Image image;
      if (isActive()) {
        if (myActiveImage == null || myActiveImage.getHeight() != r.height) {
          myActiveImage = drawToBuffer(true, r.height);
        }
        image = myActiveImage;
      }
      else {
        if (myImage == null || myImage.getHeight() != r.height) {
          myImage = drawToBuffer(false, r.height);
        }
        image = myImage;
      }

      Graphics2D g2d = (Graphics2D)g;
      Rectangle clipBounds = g2d.getClip().getBounds();
      for (int x = clipBounds.x; x < clipBounds.x + clipBounds.width; x += 150) {
        g2d.drawImage(image, x, 0, null);
      }
    }

    protected boolean isActive() {
      return LightToolWindow.this.isActive();
    }
  }

  private static BufferedImage drawToBuffer(boolean active, int height) {
    final int width = 150;

    BufferedImage image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    UIUtil.drawHeader(g, 0, width, height, active, true, false, false);
    g.dispose();

    return image;
  }
}