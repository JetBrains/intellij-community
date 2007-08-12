/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.labels.BoldLabel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.ui.popup.util.ElementFilter;
import com.intellij.ui.popup.util.MnemonicsSearch;
import com.intellij.ui.popup.util.SpeedSearch;
import com.intellij.util.ui.BlockBorder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Method;

public abstract class BasePopup implements ActionListener, ElementFilter, JBPopup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.popup.BasePopup");

  private static final int AUTO_POPUP_DELAY = 1000;
  private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, 600);

  protected static final int STEP_X_PADDING = 2;

  private Popup myPopup;

  private BasePopup myParent;

  protected JPanel myContainer;

  protected final PopupStep<Object> myStep;
  protected BasePopup myChild;

  private JScrollPane myScrollPane;
  @NotNull
  private JLabel myTitle;

  protected JComponent myContent;

  private Timer myAutoSelectionTimer = new Timer(AUTO_POPUP_DELAY, this);

  private final SpeedSearch mySpeedSearch = new SpeedSearch() {
    protected void update() {
      onSpeedSearchPatternChanged();
      mySpeedSearchPane.update();
    }
  };

  private SpeedSearchPane mySpeedSearchPane;

  private MnemonicsSearch myMnemonicsSearch;
  private Object myParentValue;

  private FocusTrackback myFocusTrackback;
  private Component myOwner;
  private Point myLastOwnerPoint;
  private Window myOwnerWindow;
  private MyComponentAdapter myOwnerListener;

  public BasePopup(PopupStep aStep) {
    this(null, aStep);
  }

  public BasePopup(JBPopup aParent, PopupStep aStep) {
    myParent = (BasePopup) aParent;
    myStep = aStep;

    if (myStep.isSpeedSearchEnabled() && myStep.isMnemonicsNavigationEnabled()) {
      throw new IllegalArgumentException("Cannot have both options enabled at the same time: speed search and mnemonics navigation");
    }

    mySpeedSearch.setEnabled(myStep.isSpeedSearchEnabled());

    myContainer = new MyContainer();

    mySpeedSearchPane = new SpeedSearchPane(this);

    myContent = createContent();

    myScrollPane = new JScrollPane(myContent);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.getHorizontalScrollBar().setBorder(null);

    myScrollPane.getActionMap().get("unitScrollLeft").setEnabled(false);
    myScrollPane.getActionMap().get("unitScrollRight").setEnabled(false);

    myScrollPane.setBorder(null);
    myContainer.add(myScrollPane, BorderLayout.CENTER);

    if (!SystemInfo.isMac) {
      myContainer.setBorder(new BlockBorder());
    }

    final String title = aStep.getTitle();
    if (title == null) {
      myTitle = new JLabel();
    }
    else {
      myTitle = new BoldLabel(title);
      myTitle.setHorizontalAlignment(SwingConstants.CENTER);
      myTitle.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
      //myTitle.setBackground(TITLE_BACKGROUND);
    }
    myTitle.setOpaque(true);
    myContainer.add(myTitle, BorderLayout.NORTH);

    registerAction("disposeAll", KeyEvent.VK_ESCAPE, InputEvent.SHIFT_MASK, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (mySpeedSearch.isHoldingFilter()) {
          mySpeedSearch.reset();
        }
        else {
          disposeAll();
        }
      }
    });

    AbstractAction goBackAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        goBack();
      }
    };

    registerAction("goBack3", KeyEvent.VK_ESCAPE, 0, goBackAction);

    myMnemonicsSearch = new MnemonicsSearch(this) {
      protected void select(Object value) {
        onSelectByMnemonic(value);
      }
    };



  }

  private void disposeAll() {
    BasePopup root = PopupDispatcher.getActiveRoot();
    disposeAllParents();
    root.getStep().canceled();
  }

  public void goBack() {
    if (mySpeedSearch.isHoldingFilter()) {
      mySpeedSearch.reset();
      return;
    }

    if (myParent != null) {
      myParent.disposeChildren();
    }
    else {
      disposeAll();
    }
  }

  protected abstract JComponent createContent();

  public void dispose() {
    myAutoSelectionTimer.stop();

    if (myPopup == null) return;

    myPopup.hide();
    mySpeedSearchPane.dispose();

    PopupDispatcher.unsetShowing(this);
    PopupDispatcher.clearRootIfNeeded(this);

    myPopup = null;
    myContainer = null;

    if (myParent == null) {
      myFocusTrackback.restoreFocus();
    }

    if (myOwnerWindow != null && myOwnerListener != null) {
      myOwnerWindow.removeComponentListener(myOwnerListener);
    }
  }

  public void disposeChildren() {
    if (myChild != null) {
      myChild.disposeChildren();
      myChild.dispose();
      myChild = null;
    }
  }

  public final void show(@NotNull RelativePoint aPoint) {
    final Point screenPoint = aPoint.getScreenPoint();
    show(aPoint.getComponent(), screenPoint.x, screenPoint.y);
  }

  public void showInScreenCoordinates(@NotNull Component owner, @NotNull Point point) {
    show(owner, point.x, point.y);
  }

  public void showInBestPositionFor(@NotNull DataContext dataContext) {
    show(JBPopupFactory.getInstance().guessBestPopupLocation(dataContext));
  }

  public void showInBestPositionFor(@NotNull Editor editor) {
    show(JBPopupFactory.getInstance().guessBestPopupLocation(editor));
  }

  protected boolean beforeShow() {
    return true;
  }

  protected void afterShow() {

  }

  public void show(final Component owner) {
    showInCenterOf(owner);
  }

  public final void show(Component owner, int aScreenX, int aScreenY) {
    if (myContainer == null) {
      throw new IllegalStateException("Wizard dialog was already disposed. Recreate a new instance to show the wizard again");
    }

    myFocusTrackback = new FocusTrackback(this, owner, true);

    myScrollPane.getViewport().setPreferredSize(myContent.getPreferredSize());
    boolean shouldShow = beforeShow();
    if (!shouldShow) {
      myFocusTrackback.setMustBeShown(false);
    }

    Rectangle targetBounds = new Rectangle(new Point(aScreenX, aScreenY), myContainer.getPreferredSize());
    ScreenUtil.moveRectangleToFitTheScreen(targetBounds);

    if (getParent() != null) {
      if (getParent().getBounds().intersects(targetBounds)) {
        targetBounds.x = getParent().getBounds().x - targetBounds.width - STEP_X_PADDING;
      }
    }

    if (getParent() == null) {
      PopupDispatcher.setActiveRoot(this);
    }
    else {
      PopupDispatcher.setShowing(this);
    }

    myPopup = setupPopupFactory().getPopup(owner, myContainer, targetBounds.x, targetBounds.y);


    myOwner = owner;

    if (shouldShow) {
      myPopup.show();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          requestFocus();
          myFocusTrackback.registerFocusComponent(getPreferredFocusableComponent());
          registerAutoMove();
          afterShow();
        }
      });
    }
    else {
      cancel();
    }
  }

  private void registerAutoMove() {
    if (myOwner != null) {
      myOwnerWindow = SwingUtilities.getWindowAncestor(myOwner);
      if (myOwnerWindow != null) {
        myLastOwnerPoint = myOwnerWindow.getLocationOnScreen();
        myOwnerListener = new MyComponentAdapter();
        myOwnerWindow.addComponentListener(myOwnerListener);
      }
    }
  }

  private void processParentWindowMoved() {
    if (myPopup == null || myContainer == null) return;

    final Point newOwnerPoint = myOwnerWindow.getLocationOnScreen();

    int deltaX = myLastOwnerPoint.x - newOwnerPoint.x;
    int deltaY = myLastOwnerPoint.y - newOwnerPoint.y;

    myLastOwnerPoint = newOwnerPoint;

    final Window wnd = SwingUtilities.getWindowAncestor(myContainer);
    final Point current = wnd.getLocationOnScreen();

    setLocation(new Point(current.x - deltaX, current.y - deltaY));
  }

  private static PopupFactory setupPopupFactory() {
    final PopupFactory factory = PopupFactory.getSharedInstance();
    if (!SystemInfo.isWindows) {
      try {
        final Method method = PopupFactory.class.getDeclaredMethod("setPopupType", int.class);
        method.setAccessible(true);
        method.invoke(factory, 2);
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
    return factory;
  }

  protected abstract void requestFocus();

  protected abstract JComponent getPreferredFocusableComponent();

  public boolean canClose() {
    return true;
  }

  public void cancel() {
    disposeChildren();
    dispose();
    getStep().canceled();
  }

  public boolean isVisible() {
    return myPopup != null;
  }

  public Component getContent() {
    return myContent;
  }

  public void showInCenterOf(@NotNull Component aContainer) {
    final JComponent component = getTargetComponent(aContainer);

    Point containerScreenPoint = component.getVisibleRect().getLocation();
    SwingUtilities.convertPointToScreen(containerScreenPoint, aContainer);

    final Point popupPoint =
      getCenterPoint(new Rectangle(containerScreenPoint, component.getVisibleRect().getSize()), myContainer.getPreferredSize());
    show(aContainer, popupPoint.x, popupPoint.y);
  }

  private static JComponent getTargetComponent(Component aComponent) {
    if (aComponent instanceof JComponent) {
      return (JComponent)aComponent;
    }
    else if (aComponent instanceof JFrame) {
      return ((JFrame)aComponent).getRootPane();
    }
    else {
      return ((JDialog)aComponent).getRootPane();
    }
  }

  public void showCenteredInCurrentWindow(@NotNull Project project) {
    Window window = null;

    Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    if (focusedComponent != null) {
      if (focusedComponent instanceof Window) {
        window = (Window)focusedComponent;
      }
      else {
        window = SwingUtilities.getWindowAncestor(focusedComponent);
      }
    }
    if (window == null) {
      window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    }

    showInCenterOf(window);
  }

  public void showUnderneathOf(@NotNull Component aComponent) {
    final JComponent component = getTargetComponent(aComponent);

    final Point point = aComponent.getLocationOnScreen();
    point.y += component.getVisibleRect().height;
    show(aComponent, point.x, point.y);
  }

  private static Point getCenterPoint(Rectangle aContainerRec, Dimension aPopupSize) {
    Point result = new Point();

    Point containerLocation = aContainerRec.getLocation();
    Dimension containerSize = aContainerRec.getSize();

    result.x = containerLocation.x + (containerSize.width / 2 - aPopupSize.width / 2);
    result.y = containerLocation.y + (containerSize.height / 2 - aPopupSize.height / 2);

    return result;
  }

  protected void disposeAllParents() {
    dispose();
    if (myParent != null) {
      myParent.disposeAllParents();
    }
  }

  public final void registerAction(@NonNls String aActionName, int aKeyCode, int aModifier, Action aAction) {
    getInputMap().put(KeyStroke.getKeyStroke(aKeyCode, aModifier), aActionName);
    getActionMap().put(aActionName, aAction);
  }

  protected abstract InputMap getInputMap();

  protected abstract ActionMap getActionMap();

  protected final void setParentValue(Object parentValue) {
    myParentValue = parentValue;
  }

  private static class MyContainer extends OpaquePanel implements DataProvider {
    MyContainer() {
      super(new BorderLayout(), Color.white);
      setFocusCycleRoot(true);
    }

    public Object getData(String dataId) {
      return null;
    }

    public Dimension getPreferredSize() {
      return computeNotBiggerDimension(super.getPreferredSize());
    }

    private static Dimension computeNotBiggerDimension(Dimension ofContent) {
      int resultWidth = ofContent.width > MAX_SIZE.width ? MAX_SIZE.width : ofContent.width;
      int resultHeight = ofContent.height > MAX_SIZE.height ? MAX_SIZE.height : ofContent.height;

      return new Dimension(resultWidth, resultHeight);
    }
  }

  public BasePopup getParent() {
    return myParent;
  }

  public PopupStep getStep() {
    return myStep;
  }

  public final void dispatch(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED) {
      return;
    }
    final KeyStroke stroke = KeyStroke.getKeyStroke(event.getKeyChar(), event.getModifiers(), false);
    if (getInputMap().get(stroke) != null) {
      final Action action = getActionMap().get(getInputMap().get(stroke));
      if (action.isEnabled()) {
        action.actionPerformed(new ActionEvent(myContent, event.getID(), "", event.getWhen(), event.getModifiers()));
        return;
      }
    }

    process(event);
    mySpeedSearch.process(event);
    myMnemonicsSearch.process(event);
  }

  protected void process(KeyEvent aEvent) {

  }

  public Rectangle getBounds() {
    return new Rectangle(myContainer.getLocationOnScreen(), myContainer.getSize());
  }

//  public Window getWindow() {
//    return myPopup;
//  }

  public JComponent getContainer() {
    return myContainer;
  }

  protected static BasePopup createPopup(BasePopup parent, PopupStep step, Object parentValue) {
    if (step instanceof ListPopupStep) {
      return new ListPopupImpl(parent, (ListPopupStep)step, parentValue);
    }
    else if (step instanceof TreePopupStep) {
      return new TreePopupImpl(parent, (TreePopupStep)step, parentValue);
    }
    else {
      throw new IllegalArgumentException(step.getClass().toString());
    }
  }

  public final void actionPerformed(ActionEvent e) {
    myAutoSelectionTimer.stop();
    if (getStep().isAutoSelectionEnabled()) {
      onAutoSelectionTimer();
    }
  }

  protected final void restartTimer() {
    if (!myAutoSelectionTimer.isRunning()) {
      myAutoSelectionTimer.start();
    }
    else {
      myAutoSelectionTimer.restart();
    }
  }

  protected final void stopTimer() {
    myAutoSelectionTimer.stop();
  }

  protected void onAutoSelectionTimer() {

  }

  protected void onSpeedSearchPatternChanged() {
  }

  public boolean shouldBeShowing(Object value) {
    if (!myStep.isSpeedSearchEnabled()) return true;
    SpeedSearchFilter<Object> filter = myStep.getSpeedSearchFilter();
    if (!filter.canBeHidden(value)) return true;
    String text = filter.getIndexedString(value);
    return mySpeedSearch.shouldBeShowing(text);
  }

  public SpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }

  @NotNull
  JLabel getTitle() {
    return myTitle;
  }

  protected void onSelectByMnemonic(Object value) {

  }

  protected abstract void onChildSelectedFor(Object value);

  protected final void notifyParentOnChildSelection() {
    if (myParent == null || myParentValue == null) return;
    myParent.onChildSelectedFor(myParentValue);
  }

  public void setLocation(@NotNull final Point screenPoint) {
    JBPopupImpl.moveTo(myContainer, screenPoint, null);
  }

  public void setSize(@NotNull final Dimension size) {
    JBPopupImpl.setSize(myContainer, size);
  }

  private class MyComponentAdapter extends ComponentAdapter {
    public void componentMoved(final ComponentEvent e) {
      processParentWindowMoved();
    }
  }
}
