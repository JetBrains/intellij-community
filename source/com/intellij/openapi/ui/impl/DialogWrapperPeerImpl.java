package com.intellij.openapi.ui.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocatorEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.ui.SpeedSearchBase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;

public class DialogWrapperPeerImpl extends DialogWrapperPeer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.DialogWrapper");

  private DialogWrapper myWrapper;
  private MyDialog myDialog;
  private boolean myCanBeParent = true;
  /*
   * Default dialog's actions.
   */
  private WindowManagerEx myWindowManager;
  private Project myProject;

  /**
   * Creates modal <code>DialogWrapper</code>. The currently active window will be the dialog's parent.
   * 
   * @param project     parent window for the dialog will be calculated based on focused window for the
   *                    specified <code>project</code>. This parameter can be <code>null</code>. In this case parent window
   *                    will be suggested based on current focused window.
   * @param canBeParent specifies whether the dialog can be parent for other windows. This parameter is used
   *                    by <code>WindowManager</code>.
   */
  protected DialogWrapperPeerImpl(DialogWrapper wrapper, Project project, boolean canBeParent) {
    myWrapper = wrapper;
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }

    Window window = null;
    if (myWindowManager != null) {

      if (project == null) {
        project = (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
      }

      myProject = project;
      window = myWindowManager.suggestParentWindow(myProject);
      if (window == null) {
        Window focusedWindow = myWindowManager.getMostRecentFocusedWindow();
        if (focusedWindow instanceof IdeFrame) {
          window = focusedWindow;
        }
      }
    }

    Window owner;
    if (window != null) {
      owner = window;
    }
    else {
      owner = JOptionPane.getRootFrame();
    }

    createDialog(owner, canBeParent);
  }

  protected DialogWrapperPeerImpl(DialogWrapper wrapper, boolean canBeParent) {
    this(wrapper, (Project)null, canBeParent);
  }

  /**
   * @param parent parent component whicg is used to canculate heavy weight window ancestor.
   *               <code>parent</code> cannot be <code>null</code> and must be showing.
   */
  protected DialogWrapperPeerImpl(DialogWrapper wrapper, Component parent, boolean canBeParent) {
    myWrapper = wrapper;
    if (parent == null) {
      throw new IllegalArgumentException("parent cannot be null");
    }
    if (!parent.isShowing()) {
      throw new IllegalArgumentException("parent must be showing: " + parent);
    }
    myWindowManager = null;
    Application application = ApplicationManager.getApplication();
    if (application != null && application.hasComponent(WindowManager.class)) {
      myWindowManager = (WindowManagerEx)WindowManager.getInstance();
    }

    Window owner = parent instanceof Window
                   ? (Window)parent
                   : (Window)SwingUtilities.getAncestorOfClass(Window.class, parent);
    if (!(owner instanceof Dialog) && !(owner instanceof Frame)) {
      owner = JOptionPane.getRootFrame();
    }
    createDialog(owner, canBeParent);
  }

  public void setUndecorated(boolean undecorated) {
    myDialog.setUndecorated(undecorated);
  }

  public void addMouseListener(MouseListener listener) {
    myDialog.addMouseListener(listener);
  }

  public void addMouseListener(MouseMotionListener listener) {
    myDialog.addMouseMotionListener(listener);
  }

  public void addKeyListener(KeyListener listener) {
    myDialog.addKeyListener(listener);
  }

  private void createDialog(Window owner, boolean canBeParent) {
    if (owner instanceof Frame) {
      myDialog = new MyDialog((Frame)owner, myWrapper);
    }
    else {
      myDialog = new MyDialog((Dialog)owner, myWrapper);
    }
    myDialog.setModal(true);
    myCanBeParent = canBeParent;

  }


  public void toFront() {
    myDialog.toFront();
  }

  public void toBack() {
    myDialog.toBack();
  }

  protected void dispose() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");
    myDialog.remove(myDialog.getRootPane());

    Runnable disposer = new Runnable() {
      public void run() {
        myDialog.dispose();
        /*
        if (myWindowManager == null) {
          myDialog.dispose();
        }
        else {
          myWindowManager.hideDialog(myDialog, myProject);
        }
        */
      }
    };

    if (EventQueue.isDispatchThread()) {
      disposer.run();
    }
    else {
      SwingUtilities.invokeLater(disposer);
    }
  }

  private boolean isProgressDialog() {
    return myWrapper.isModalProgress();
  }

  public Container getContentPane() {
    return myDialog.getContentPane();
  }

  /**
   * @see javax.swing.JDialog#validate
   */
  public void validate() {
    myDialog.validate();
  }

  /**
   * @see javax.swing.JDialog#repaint
   */
  public void repaint() {
    myDialog.repaint();
  }

  public Window getOwner() {
    return myDialog.getOwner();
  }

  public Window getWindow() {
    return myDialog;
  }

  public JRootPane getRootPane() {
    return myDialog.getRootPane();
  }

  public Dimension getSize() {
    return myDialog.getSize();
  }

  public String getTitle() {
    return myDialog.getTitle();
  }

  /**
   * @see java.awt.Window#pack
   */
  public void pack() {
    myDialog.pack();
  }

  public Dimension getPreferredSize() {
    return myDialog.getPreferredSize();
  }

  public void setModal(boolean modal) {
    myDialog.setModal(modal);
  }

  public boolean isVisible() {
    return myDialog.isVisible();
  }

  public boolean isShowing() {
    return myDialog.isShowing();
  }

  public void setSize(int width, int height) {
    myDialog.setSize(width, height);
  }

  public void setTitle(String title) {
    myDialog.setTitle(title);
  }

  public void isResizable() {
    myDialog.isResizable();
  }

  public void setResizable(boolean resizable) {
    myDialog.setResizable(resizable);
  }

  public Point getLocation() {
    return myDialog.getLocation();
  }

  public void setLocation(Point p) {
    myDialog.setLocation(p);
  }

  public void setLocation(int x, int y) {
    myDialog.setLocation(x, y);
  }

  public void show() {
    LOG.assertTrue(EventQueue.isDispatchThread(), "Access is allowed from event dispatch thread only");

    new AnCancelAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)), getRootPane());

    if (!myCanBeParent && myWindowManager != null) {
      myWindowManager.doNotSuggestAsParent(myDialog);
    }

    if (myDialog.isModal() && !isProgressDialog()) {
      /* TODO: Temporarily disable due to J2EE dialogs. Lots of code to rewrite there.
      if (ApplicationManager.getApplication() != null) { // [dsl] for license dialog
        if (ApplicationManager.getApplication().getCurrentWriteAction(null) != null) {
          LOG.error(
            "Showing of modal dialog is prohibited inside write-action, modalityState=" + ModalityState.current());
        }
      }
      */
      ((CommandProcessorEx)CommandProcessor.getInstance()).enterModal();
      LaterInvocatorEx.enterModal(myDialog);
    }

    try {
      myDialog.show();
    }
    finally {
      if (myDialog.isModal() && !isProgressDialog()) {
        ((CommandProcessorEx)CommandProcessor.getInstance()).leaveModal();
        LaterInvocatorEx.leaveModal(myDialog);
      }
    }
  }

  private class AnCancelAction extends AnAction {
    public void update(AnActionEvent e) {
      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      e.getPresentation().setEnabled(false);
      if (focusOwner instanceof JComponent && SpeedSearchBase.hasActiveSpeedSearch((JComponent)focusOwner)) {
        return;
      }
      
      if (focusOwner instanceof JTree) {
        JTree tree = (JTree)focusOwner;
        if (!tree.isEditing()) {
          e.getPresentation().setEnabled(true);
        }
      }
      else if (focusOwner instanceof JTable) {
        JTable table = (JTable)focusOwner;
        if (!table.isEditing()) {
          e.getPresentation().setEnabled(true);
        }
      }
    }

    public void actionPerformed(AnActionEvent e) {
      myWrapper.doCancelAction();
    }
  }


  private static class MyDialog extends JDialog implements DialogWrapperDialog, DataProvider {
    private final WeakReference<DialogWrapper> myDialogWrapper;
    /**
     * Initial size of the dialog. When the dialog is being closed and
     * current size of the dialog is not equals to the initial sizethen the
     * current (changed) size is stored in the <code>DimensionService</code>.
     */
    private Dimension myInitialSize;
    private String myDimensionServiceKey;

    public MyDialog(Dialog owner, DialogWrapper dialogWrapper) {
      super(owner);
      myDialogWrapper = new WeakReference<DialogWrapper>(dialogWrapper);
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      addWindowListener(new MyWindowListener());
    }

    public MyDialog(Frame owner, DialogWrapper dialogWrapper) {
      super(owner);
      myDialogWrapper = new WeakReference<DialogWrapper>(dialogWrapper);
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      addWindowListener(new MyWindowListener());
    }

    public DialogWrapper getDialogWrapper() {
      return myDialogWrapper.get();
    }

    public void centerInParent() {
      setLocationRelativeTo(getOwner());
    }

    public Object getData(String dataId) {
      final DialogWrapper wrapper = myDialogWrapper.get();
      if (wrapper instanceof DataProvider) {
        return ((DataProvider)wrapper).getData(dataId);
      }
      return null;
    }

    protected JRootPane createRootPane() {
      return new JRootPane() {
        public void reshape(int x, int y, int width, int height) {
          Dimension minSize = getMinimumSize();
          if (width < minSize.width || height < minSize.height) {
            Window window = MyDialog.this;

            Dimension size = window.getSize();
            if (width < minSize.width) {
              size.width = size.width - width + minSize.width;
              width = minSize.width;
            }

            if (height < minSize.height) {
              size.height = size.height - height + minSize.height;
              height = minSize.height;
            }

            window.setSize(size);
          }

          super.reshape(x, y, width, height);
        }
      };
    }

    public void show() {
      final DialogWrapper dialogWrapper = getDialogWrapper();

      pack();
      setSize((int)(getWidth() * dialogWrapper.getHorizontalStretch()),
              (int)(getHeight() * dialogWrapper.getVerticalStretch()));

      // Restore dialog's size and location

      myDimensionServiceKey = dialogWrapper.getDimensionKey();
      Point location = null;

      if (myDimensionServiceKey != null) {
        location = DimensionService.getInstance().getLocation(myDimensionServiceKey);
        Dimension size = DimensionService.getInstance().getSize(myDimensionServiceKey);
        if (size != null) {
          myInitialSize = (Dimension)size.clone();
          setSize(myInitialSize);
        }
      }

      if (myInitialSize == null) {
        myInitialSize = getSize();
      }

      if (location == null) {
        location = dialogWrapper.getInitialLocation();
      }

      if (location != null) {
        setLocation(location);
      }
      else {
        setLocationRelativeTo(getOwner());
      }

      // Request focus into preferred component, move mouse of default button (if configured), etc

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          // Do nothing if dialog was already disposed
          DialogWrapper dialogWrapper = getDialogWrapper();
          if (dialogWrapper == null || !dialogWrapper.isShowing()) {
            return;
          }

          JButton defaultButton = getRootPane().getDefaultButton();
          JComponent component = dialogWrapper.getPreferredFocusedComponent();
          if (component == null) {
            component = defaultButton;
          }
          if (component != null) {
            component.requestFocus();
          }
          //
          Application application = ApplicationManager.getApplication();
          if (application != null && application.hasComponent(UISettings.class)) {
            if (defaultButton != null && UISettings.getInstance().MOVE_MOUSE_ON_DEFAULT_BUTTON) {
              Point p = defaultButton.getLocationOnScreen();
              Rectangle r = defaultButton.getBounds();
              try {
                Robot robot = new Robot();
                robot.mouseMove(p.x + r.width / 2, p.y + r.height / 2);
              }
              catch (AWTException exc) {
                exc.printStackTrace();
              }
            }
          }
        }
      });

      super.show();
    }

    private class MyWindowListener extends WindowAdapter {
      public void windowClosing(WindowEvent e) {
        DialogWrapper dialogWrapper = getDialogWrapper();
        if (dialogWrapper.shouldCloseOnCross()) {
          dialogWrapper.doCancelAction();
        }
      }

      public void windowClosed(WindowEvent e) {
        if (myDimensionServiceKey != null && myInitialSize != null) { // myInitialSize can be null only if dialog is disposed before first showing
          Point location = getLocation();
          // Save location
          DimensionService.getInstance().setLocation(myDimensionServiceKey, location);
          // Save size
          Dimension size = getSize();
          if (!myInitialSize.equals(size)) {
            DimensionService.getInstance().setSize(myDimensionServiceKey, size);
          }
        }
      }

      public void windowOpened(WindowEvent e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            // Do nothing if dialog was already disposed
            DialogWrapper dialogWrapper = getDialogWrapper();
            if (dialogWrapper == null || !dialogWrapper.isShowing()) {
              return;
            }

            selectPreferredFocusedComponent(dialogWrapper.getPreferredFocusedComponent());
          }
        });
      }
    }
  }

  private static void selectPreferredFocusedComponent(final JComponent component) {
    if (component instanceof JTextField) {
      JTextField field = (JTextField)component;
      String text = field.getText();
      if (text != null) {
        field.setSelectionStart(0);
        field.setSelectionEnd(text.length());
      }
    }
    else if (component instanceof JComboBox) {
      JComboBox combobox = (JComboBox)component;
      combobox.getEditor().selectAll();
    }
  }

  public void setContentPane(JComponent content) {
    myDialog.setContentPane(content);
  }

  public void centerInParent() {
    myDialog.centerInParent();
  }
}
