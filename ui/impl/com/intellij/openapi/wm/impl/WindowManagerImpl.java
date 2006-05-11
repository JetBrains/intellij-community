package com.intellij.openapi.wm.impl;

import com.intellij.Patches;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.Alarm;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */

public final class WindowManagerImpl extends WindowManagerEx implements ApplicationComponent, NamedJDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.WindowManagerImpl");
  private static boolean ourAlphaModeLibraryLoaded;
  @NonNls private static final String FOCUSED_WINDOW_PROPERTY_NAME = "focusedWindow";
  @NonNls private static final String X_ATTR = "x";
  @NonNls private static final String FRAME_ELEMENT = "frame";
  @NonNls private static final String Y_ATTR = "y";
  @NonNls private static final String WIDTH_ATTR = "width";
  @NonNls private static final String HEIGHT_ATTR = "height";
  @NonNls private static final String EXTENDED_STATE_ATTR = "extended-state";

  static {
    initialize();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void initialize() {
    try {
      System.loadLibrary("jawt");
      System.loadLibrary("transparency");
      ourAlphaModeLibraryLoaded = true;
    }
    catch (Throwable exc) {
      ourAlphaModeLibraryLoaded = false;
    }
  }

  /**
   * Union of bounds of all available default screen devices.
   */
  private Rectangle myScreenBounds;

  private final CommandProcessor myCommandProcessor;
  private final WindowWatcher myWindowWatcher;
  /**
   * That is the default layout.
   */
  private DesktopLayout myLayout;

  private final HashMap<Project, IdeFrame> myProject2Frame;

  private final HashMap<Project, Set<JDialog>> myDialogsToDispose;

  /**
   * This members is needed to read frame's bounds from XML.
   * <code>myFrameBounds</code> can be <code>null</code>.
   */
  private Rectangle myFrameBounds;
  private int myFrameExtendedState;
  private WindowAdapter myActivationListener;
  private final ApplicationInfoEx myApplicationInfoEx;
  private final DataManager myDataManager;
  private final ActionManager myActionManager;
  private final UISettings myUiSettings;
  private final KeymapManager myKeymapManager;

  /**
   * invoked by reflection
   * @param dataManager
   * @param applicationInfoEx
   * @param actionManager
   * @param uiSettings
   * @param keymapManager
   */
  public WindowManagerImpl(DataManager dataManager,
                              ApplicationInfoEx applicationInfoEx,
                              ActionManager actionManager,
                              UISettings uiSettings,
                              KeymapManager keymapManager) {
    myApplicationInfoEx = applicationInfoEx;
    myDataManager = dataManager;
    myActionManager = actionManager;
    myUiSettings = uiSettings;
    myKeymapManager = keymapManager;
    if (myDataManager instanceof DataManagerImpl) {
        ((DataManagerImpl)myDataManager).setWindowManager(this);
    }

    myCommandProcessor = new CommandProcessor();
    myWindowWatcher = new WindowWatcher();
    final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    keyboardFocusManager.addPropertyChangeListener(FOCUSED_WINDOW_PROPERTY_NAME, myWindowWatcher);
    if (Patches.SUN_BUG_ID_4218084) {
      keyboardFocusManager.addPropertyChangeListener(FOCUSED_WINDOW_PROPERTY_NAME, new SUN_BUG_ID_4218084_Patch());
    }
    myLayout = new DesktopLayout();
    myProject2Frame = new HashMap<Project, IdeFrame>();
    myDialogsToDispose = new HashMap<Project, Set<JDialog>>();
    myFrameExtendedState = Frame.NORMAL;

    // Calculate screen bounds.

    myScreenBounds = new Rectangle();
    if (!GraphicsEnvironment.isHeadless()) {
      final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      final GraphicsDevice[] devices = env.getScreenDevices();
      for (final GraphicsDevice device : devices) {
        myScreenBounds = myScreenBounds.union(device.getDefaultConfiguration().getBounds());
      }
    }

    myActivationListener = new WindowAdapter() {
      public void windowActivated(WindowEvent e) {
        Window activeWindow = e.getWindow();
        if (activeWindow instanceof IdeFrame) { // must be
          proceedDialogDisposalQueue(((IdeFrame)activeWindow).getProject());
        }
      }
    };
  }

  public void showFrame() {
    IdeEventQueue.getInstance().setWindowManager(this);
    final IdeFrame frame = new IdeFrame(myApplicationInfoEx, myActionManager, myUiSettings, myDataManager, myKeymapManager);
    myProject2Frame.put(null, frame);
    if (myFrameBounds != null) {
      frame.setBounds(myFrameBounds);
    }
    frame.setVisible(true);
    frame.setExtendedState(myFrameExtendedState);
  }

  public IdeFrame[] getAllFrames() {
    final Collection<IdeFrame> ideFrames = myProject2Frame.values();
    return ideFrames.toArray(new IdeFrame[ideFrames.size()]);
  }

  public final Rectangle getScreenBounds() {
    return myScreenBounds;
  }

  public final boolean isInsideScreenBounds(final int x, final int y, final int width) {
    return
      x >= myScreenBounds.x + 50 - width &&
      y >= myScreenBounds.y - 50 &&
      x <= myScreenBounds.x + myScreenBounds.width - 50 &&
      y <= myScreenBounds.y + myScreenBounds.height - 50;
  }

  public final boolean isInsideScreenBounds(final int x, final int y) {
    return myScreenBounds.contains(x, y);
  }

  public final boolean isAlphaModeSupported() {
    return ourAlphaModeLibraryLoaded && (SystemInfo.isWindows2000 || SystemInfo.isWindowsXP);
  }

  public final void setAlphaModeRatio(final Window window, final float ratio) {
    if (!window.isDisplayable() || !window.isShowing()) {
      throw new IllegalArgumentException("window must be displayable and showing. window=" + window);
    }
    if (ratio < 0.0f || ratio > 1.0f) {
      throw new IllegalArgumentException("ratio must be in [0..1] range. ratio=" + ratio);
    }
    if (!isAlphaModeSupported() || !isAlphaModeEnabled(window)) {
      return;
    }
    setAlphaModeRatioWin32Impl(window, 255 - (int)(255f * ratio));
  }

  private static native void setAlphaModeRatioWin32Impl(Window window, int ratio);

  public final boolean isAlphaModeEnabled(final Window window) {
    if (!window.isDisplayable() || !window.isShowing()) {
      throw new IllegalArgumentException("window must be displayable and showing. window=" + window);
    }
    if (!isAlphaModeSupported()) {
      return false;
    }
    else {
      return isAlphaModeEnabledWin32Impl(window);
    }
  }

  private static native boolean isAlphaModeEnabledWin32Impl(Window window);

  public final void setAlphaModeEnabled(final Window window, final boolean state) {
    if (!window.isDisplayable() || !window.isShowing()) {
      throw new IllegalArgumentException("window must be displayable and showing. window=" + window);
    }
    if (isAlphaModeSupported()) {
      setAlphaModeEnabledWin32Impl(window, state);
    }
  }

  public void hideDialog(JDialog dialog, Project project) {
    if (project == null) {
      dialog.dispose();
    }
    else {
      IdeFrame frame = getFrame(project);
      if (frame.isActive()) {
        dialog.dispose();
      }
      else {
        queueForDisposal(dialog, project);
        dialog.setVisible(false);
      }
    }
  }

  private static native void setAlphaModeEnabledWin32Impl(Window window, boolean state);

  public final void disposeComponent() {}

  public final void initComponent() {
  }

  public final void doNotSuggestAsParent(final Window window) {
    myWindowWatcher.doNotSuggestAsParent(window);
  }

  public final void dispatchComponentEvent(final ComponentEvent e) {
    myWindowWatcher.dispatchComponentEvent(e);
  }

  public final Window suggestParentWindow(final Project project) {
    return myWindowWatcher.suggestParentWindow(project);
  }

  public final StatusBar getStatusBar(final Project project) {
    if (!myProject2Frame.containsKey(project)) {
      return null;
    }
    final IdeFrame frame = getFrame(project);
    LOG.assertTrue(frame != null);
    return frame.getStatusBar();
  }

  public final IdeFrame getFrame(final Project project) {
    // no assert! otherwise WindowWatcher.suggestParentWindow fails for default project
    //LOG.assertTrue(myProject2Frame.containsKey(project));
    return myProject2Frame.get(project);
  }

  public final IdeFrame allocateFrame(final Project project) {
    LOG.assertTrue(!myProject2Frame.containsKey(project));

    final IdeFrame frame;
    if (myProject2Frame.containsKey(null)) {
      frame = myProject2Frame.get(null);
      myProject2Frame.remove(null);
      myProject2Frame.put(project, frame);
      frame.setProject(project);
    }
    else {
      frame = new IdeFrame((ApplicationInfoEx)ApplicationInfo.getInstance(), ActionManager.getInstance(), UISettings.getInstance(), DataManager.getInstance(), KeymapManagerEx.getInstance());
      if (myFrameBounds != null) {
        frame.setBounds(myFrameBounds);
      }
      frame.setProject(project);
      myProject2Frame.put(project, frame);
      frame.setVisible(true);
    }

    frame.addWindowListener(myActivationListener);

    return frame;
  }

  private void proceedDialogDisposalQueue(Project project) {
    Set<JDialog> dialogs = myDialogsToDispose.get(project);
    if (dialogs == null) return;
    for (JDialog dialog : dialogs) {
      dialog.dispose();
    }
    myDialogsToDispose.put(project, null);
  }

  private void queueForDisposal(JDialog dialog, Project project) {
    Set<JDialog> dialogs = myDialogsToDispose.get(project);
    if (dialogs == null) {
      dialogs = new HashSet<JDialog>();
      myDialogsToDispose.put(project, dialogs);
    }
    dialogs.add(dialog);
  }

  public final void releaseFrame(final IdeFrame frame) {
    final Project project = frame.getProject();
    LOG.assertTrue(project != null);

    frame.removeWindowListener(myActivationListener);
    proceedDialogDisposalQueue(project);

    frame.setProject(null);
    frame.setTitle(null);
    frame.setFileTitle(null);

    final StatusBarEx statusBar = frame.getStatusBar();
    statusBar.setStatus(null);
    statusBar.setWriteStatus(false);
    statusBar.setPosition(null);
    statusBar.updateEditorHighlightingStatus(true);

    myProject2Frame.remove(project);
    if (myProject2Frame.size() == 0) {
      myProject2Frame.put(null, frame);
    }
    else {
      frame.dispose();
    }
  }

  public final Window getMostRecentFocusedWindow() {
    return myWindowWatcher.getFocusedWindow();
  }

  public final Component getFocusedComponent(final Window window) {
    return myWindowWatcher.getFocusedComponent(window);
  }

  public final Component getFocusedComponent(final Project project) {
    return myWindowWatcher.getFocusedComponent(project);
  }

  /**
   * Private part
   */
  public final CommandProcessor getCommandProcessor() {
    return myCommandProcessor;
  }

  public final String getExternalFileName() {
    return "window.manager";
  }

  public final void readExternal(final Element element) throws InvalidDataException {
    final Element frameElement = element.getChild(FRAME_ELEMENT);
    if (frameElement != null) {
      myFrameBounds = loadFrameBounds(frameElement);
      try {
        myFrameExtendedState = Integer.parseInt(frameElement.getAttributeValue(EXTENDED_STATE_ATTR));
        if ((myFrameExtendedState & Frame.ICONIFIED) > 0) {
          myFrameExtendedState = Frame.NORMAL;
        }
      }
      catch (NumberFormatException ignored) {
        myFrameExtendedState = Frame.NORMAL;
      }
    }

    final Element desktopElement = element.getChild(DesktopLayout.TAG);
    if (desktopElement != null) {
      myLayout.readExternal(desktopElement);
    }
  }

  private static Rectangle loadFrameBounds(final Element frameElement) {
    Rectangle bounds = new Rectangle();
    try {
      bounds.x = Integer.parseInt(frameElement.getAttributeValue(X_ATTR));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    try {
      bounds.y = Integer.parseInt(frameElement.getAttributeValue(Y_ATTR));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    try {
      bounds.width = Integer.parseInt(frameElement.getAttributeValue(WIDTH_ATTR));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    try {
      bounds.height = Integer.parseInt(frameElement.getAttributeValue(HEIGHT_ATTR));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
    return bounds;
  }

  public final void writeExternal(final Element element) throws WriteExternalException {
    // Save frame bounds
    final Element frameElement = new Element(FRAME_ELEMENT);
    element.addContent(frameElement);
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    final Project project;
    if (projects.length > 0) {
      project = projects[projects.length - 1];
    }
    else {
      project = null;
    }

    final IdeFrame frame = getFrame(project);
    if (frame != null) {
      final Rectangle rectangle = frame.getBounds();
      frameElement.setAttribute(X_ATTR, Integer.toString(rectangle.x));
      frameElement.setAttribute(Y_ATTR, Integer.toString(rectangle.y));
      frameElement.setAttribute(WIDTH_ATTR, Integer.toString(rectangle.width));
      frameElement.setAttribute(HEIGHT_ATTR, Integer.toString(rectangle.height));
      frameElement.setAttribute(EXTENDED_STATE_ATTR, Integer.toString(frame.getExtendedState()));

      // Save default layout
      final Element layoutElement = new Element(DesktopLayout.TAG);
      element.addContent(layoutElement);
      myLayout.writeExternal(layoutElement);
    }
  }

  public final DesktopLayout getLayout() {
    return myLayout;
  }

  public final void setLayout(final DesktopLayout layout) {
    myLayout.copyFrom(layout);
  }

  public final String getComponentName() {
    return "WindowManager";
  }

  /**
   * We cannot clear selected menu path just by changing of focused window. Under Windows LAF
   * focused window changes sporadically when user clickes on menu item or submenu. The problem
   * is that all popups under Windows LAF always has native window ancestor. This window isn't
   * focusable but by mouse click focused window changes in this manner:
   * InitialFocusedWindow->null
   * null->InitialFocusedWindow
   * To fix this problem we use alarm to accumulate such focus events.
   */
  private static final class SUN_BUG_ID_4218084_Patch implements PropertyChangeListener {
    private final Alarm myAlarm;
    private Window myInitialFocusedWindow;
    private Window myLastFocusedWindow;
    private final Runnable myClearSelectedPathRunnable;

    public SUN_BUG_ID_4218084_Patch() {
      myAlarm = new Alarm();
      myClearSelectedPathRunnable = new Runnable() {
        public void run() {
          if (myInitialFocusedWindow != myLastFocusedWindow) {
            MenuSelectionManager.defaultManager().clearSelectedPath();
          }
        }
      };
    }

    public void propertyChange(final PropertyChangeEvent e) {
      if (myAlarm.getActiveRequestCount() == 0) {
        myInitialFocusedWindow = (Window)e.getOldValue();
        final MenuElement[] selectedPath = MenuSelectionManager.defaultManager().getSelectedPath();
        if (selectedPath.length == 0) { // there is no visible popup
          return;
        }
        Component firstComponent = null;
        for (final MenuElement menuElement : selectedPath) {
          final Component component = menuElement.getComponent();
          if (component instanceof JMenuBar) {
            firstComponent = component;
            break;
          } else if (component instanceof JPopupMenu) {
            firstComponent = ((JPopupMenu) component).getInvoker();
            break;
          }
        }
        if (firstComponent == null) {
          return;
        }
        final Window window = SwingUtilities.getWindowAncestor(firstComponent);
        if (window != myInitialFocusedWindow) { // focused window doesn't have popup
          return;
        }
      }
      myLastFocusedWindow = (Window)e.getNewValue();
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(myClearSelectedPathRunnable, 150);
    }
  }
}