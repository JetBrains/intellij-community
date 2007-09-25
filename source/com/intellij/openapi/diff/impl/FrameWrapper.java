package com.intellij.openapi.diff.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.DimensionService;
import com.intellij.ui.FocusTrackback;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Map;

public class FrameWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.FrameWrapper");
  private String myDimensionKey = null;
  private JComponent myComponent = null;
  private JComponent myPreferedFocus = null;
  private String myTitle = "";
  private Image myImage = ImageLoader.loadFromResource("/icon.png");
  private boolean myCloseOnEsc = false;
  private JFrame myFrame;
  private final Map myDatas = new HashMap();
  private final ArrayList<Disposable> myDisposables = new ArrayList<Disposable>();
  private Project myProject;
  private ProjectManagerListener myProjectListener = new MyProjectManagerListener();
  private FocusTrackback myFocusTrackback;
  private FocusWatcher myFocusWatcher;

  public FrameWrapper(@NonNls String dimensionServiceKey) {
    myDimensionKey = dimensionServiceKey;
  }

  public void setDimensionKey(String dimensionKey) { myDimensionKey = dimensionKey; }

  public void setData(String dataId, Object data) { myDatas.put(dataId, data); }

  public void setProject(Project project) {
    myProject = project;
    setData(DataConstants.PROJECT, project);
    ProjectManager.getInstance().addProjectManagerListener(project, myProjectListener);
  }

  public void show() {
    final JFrame frame = getFrame();

    myFocusTrackback = new FocusTrackback(this, null, true);

    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    WindowAdapter focusListener = new WindowAdapter() {
      public void windowOpened(WindowEvent e) {
        if (myPreferedFocus != null) {
          myPreferedFocus.requestFocusInWindow();
          myFocusTrackback.registerFocusComponent(myPreferedFocus);
        }
      }
    };
    frame.addWindowListener(focusListener);
    if (myCloseOnEsc) addDisposeOnEsc(frame);
    frame.getContentPane().add(myComponent, BorderLayout.CENTER);
    frame.setTitle(myTitle);
    frame.setIconImage(myImage);
    loadFrameState(myDimensionKey, frame);

    myFocusWatcher = new FocusWatcher() {
      protected void focusLostImpl(final FocusEvent e) {
        myFocusTrackback.consume();
      }
    };
    myFocusWatcher.install(myComponent);

    frame.setVisible(true);
  }

  private static <WindowType extends Window & RootPaneContainer> void addDisposeOnEsc(final WindowType frame) {
    frame.getRootPane().registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
            MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
            if (selectedPath.length > 0) { // hide popup menu if any
              menuSelectionManager.clearSelectedPath();
            }
            else {
              frame.dispose();
            }
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
        JComponent.WHEN_IN_FOCUSED_WINDOW
    );
  }

  public JFrame getFrame() {
    if (myFrame == null) {
    myFrame = new MyJFrame();
    }
    return myFrame;
  }

  public void setComponent(JComponent component) { myComponent = component; }

  public void setPreferredFocusedComponent(JComponent preferedFocus) { myPreferedFocus = preferedFocus; }

  public void closeOnEsc() { myCloseOnEsc = true; }

  public void setImage(Image image) { myImage = image; }

  private void loadFrameState(String dimensionKey, JFrame frame) {
    final Point location;
    final Dimension size;
    final int extendedState;
    DimensionService dimensionService = DimensionService.getInstance();
    if (dimensionKey == null || dimensionService == null) {
      location = null;
      size = null;
      extendedState = -1;
    } else {
      location = dimensionService.getLocation(dimensionKey);
      size = dimensionService.getSize(dimensionKey);
      extendedState = dimensionService.getExtendedState(dimensionKey);
    }

    if (size != null) {
      if (location != null) frame.setLocation(location);
      frame.setSize(size);
      frame.getRootPane().revalidate();
    }
    else {
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      frame.pack();
      int height = Math.min(screenSize.height - 40, frame.getHeight());
      int width = Math.min(screenSize.width - 20, frame.getWidth());
      frame.setBounds(10, 10, width, height);
    }

    if (extendedState == JFrame.ICONIFIED || extendedState == JFrame.MAXIMIZED_BOTH) {
      frame.setExtendedState(extendedState);
    }
  }

  private void saveFrameState(String dimensionKey, JFrame frame) {
    DimensionService dimensionService = DimensionService.getInstance();
    if (dimensionKey == null || dimensionService == null) return;
    dimensionService.setLocation(dimensionKey, frame.getLocation());
    dimensionService.setSize(dimensionKey, frame.getSize());
    dimensionService.setExtendedState(dimensionKey, frame.getExtendedState());
  }

  public void setTitle(String title) { myTitle = title; }

  public void addDisposable(Disposable disposable) {
    LOG.assertTrue(!myDisposables.contains(disposable));
    myDisposables.add(disposable);
  }

  private class MyJFrame extends JFrame implements DataProvider {
    public MyJFrame() throws HeadlessException {}

    public void dispose() {
      saveFrameState(myDimensionKey, this);
      for (Disposable disposable : myDisposables) {
        disposable.dispose();
      }
      myDisposables.clear();
      myDatas.clear();
      if (myProject != null) {
        ProjectManager.getInstance().removeProjectManagerListener(myProject, myProjectListener);
        myProject = null;
      }
      myPreferedFocus = null;

      myFocusTrackback.restoreFocus();
      if (myComponent != null) {
        myFocusWatcher.deinstall(myComponent);
      }
      myFocusWatcher = null;

      super.dispose();
    }

    public Object getData(String dataId) {
      return myDatas.get(dataId);
    }

    @Override
    public void paint(Graphics g) {
      UIUtil.applyRenderingHints(g);
      super.paint(g);
    }
  }

  private class MyProjectManagerListener implements ProjectManagerListener {

    public void projectOpened(Project project) {
    }

    public boolean canCloseProject(Project project) {
      return true;
    }

    public void projectClosed(Project project) {
    }

    public void projectClosing(Project project) {
      if (project == myProject) {
        myFrame.setVisible(false);
        myFrame.dispose();
      }
    }
  }
}
