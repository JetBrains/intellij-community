package com.intellij.openapi.progress.util;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.TitlePanel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.BlockBorder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

@SuppressWarnings({"NonStaticInitializer"})
public class ProgressWindow extends BlockingProgressIndicator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressWindow");

  private static final int UPDATE_INTERVAL = 50; //msec. 20 frames per second.

  private MyDialog myDialog;
  private Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Alarm myInstallFunAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Alarm myShowWindowAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private final Project myProject;
  private final boolean myShouldShowBackground;
  private final boolean myShouldShowCancel;
  private String myCancelText;
  private JComponent myParentComponent;

  private String myTitle = null;

  private boolean myStoppedAlready = false;
  private FocusTrackback myFocusTrackback;
  private boolean myStared = false;
  private boolean myBackgrounded = false;

  public ProgressWindow(boolean shouldShowCancel, Project project) {
    this(shouldShowCancel, false, project);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, Project project) {
    this(shouldShowCancel, shouldShowBackground, project, null);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, Project project, String cancelText) {
    myShouldShowBackground = shouldShowBackground;
    myProject = project;
    myShouldShowCancel = shouldShowCancel;
    myCancelText = cancelText;
    setModalityProgress(myShouldShowBackground ? null : this);
    myFocusTrackback = new FocusTrackback(this, WindowManager.getInstance().suggestParentWindow(project));
    if(myParentComponent != null) {
      myDialog = new MyDialog(myShouldShowCancel, myShouldShowBackground, myParentComponent, myCancelText);
    } else {
      myDialog = new MyDialog(myShouldShowCancel, myShouldShowBackground, myProject, myCancelText);
    }
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, Project project, JComponent parentComponent, String cancelText) {
    myShouldShowBackground = shouldShowBackground;
    myProject = project;
    myShouldShowCancel = shouldShowCancel;
    myCancelText = cancelText;
    myParentComponent = parentComponent;
    setModalityProgress(myShouldShowBackground ? null : this);
    myFocusTrackback = new FocusTrackback(this, WindowManager.getInstance().suggestParentWindow(project));
    if(myParentComponent != null) {
      myDialog = new MyDialog(myShouldShowCancel, myShouldShowBackground, myParentComponent, myCancelText);
    } else {
      myDialog = new MyDialog(myShouldShowCancel, myShouldShowBackground, myProject, myCancelText);
    }
  }

  public synchronized void start() {
    LOG.assertTrue(!isRunning());
    LOG.assertTrue(!myStoppedAlready);

    super.start();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myShowWindowAlarm.addRequest(new Runnable() {
            public void run() {
              if (isRunning()) {
                showDialog();
              }
            }
          }, 300, ModalityState.current());
        }
      });
    }

    myStared = true;
  }

  public void startBlocking() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(!isRunning());
    LOG.assertTrue(!myStoppedAlready);

    enterModality();

    IdeEventQueue.getInstance().flushQueue();
    IdeEventQueue.getInstance().pumpEventsForHierarchy(myDialog.myPanel, new Condition<AWTEvent>() {
      public boolean value(final AWTEvent object) {
        if (myShouldShowCancel &&
            object instanceof KeyEvent &&
            object.getID() == KeyEvent.KEY_PRESSED &&
            ((KeyEvent)object).getKeyCode() == KeyEvent.VK_ESCAPE &&
            ((KeyEvent)object).getModifiers() == 0) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              ProgressWindow.this.cancel();
            }
          });
        }
        return myStared && !isRunning();
      }
    });

    exitModality();
  }

  private void showDialog() {
    if (!isRunning() || isCanceled()) {
      return;
    }
    
    if (myShouldShowBackground && GeneralSettings.getInstance().isSearchInBackground()) {
      return;
    }

    final JComponent cmp = ProgressManager.getInstance().getProvidedFunComponent(myProject, "<unknown>");
    if (cmp != null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      Runnable installer = new Runnable() {
        public void run() {
          if (isRunning() && !isCanceled() && getFraction() < 0.15 && myDialog!=null) {
            setFunComponent(cmp);
          }
        }
      };
      myInstallFunAlarm.addRequest(installer, 3000, getModalityState());
    }

    myDialog.setIndeterminate(getFraction() == 0 || isIndeterminate());
    myDialog.show();
    myDialog.myRepaintRunnable.run();
  }

  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    if (myDialog != null) {
      myDialog.setIndeterminate(indeterminate);
    }
  }

  public synchronized void stop() {
    LOG.assertTrue(!myStoppedAlready);
    myInstallFunAlarm.cancelAllRequests();

    super.stop();
    if (myDialog != null) {
      myDialog.hide();
      myFocusTrackback.restoreFocus();
    }
    myStoppedAlready = true;

    SwingUtilities.invokeLater(EmptyRunnable.INSTANCE); // Just to give blocking dispatching a chance to go out.
  }

  public void cancel() {
    super.cancel();
    if (myDialog != null) {
      myDialog.cancel();
    }
  }

  public void background() {
    if (myDialog != null) {
      GeneralSettings.getInstance().setSearchInBackground(true);
      myBackgrounded = true;
      myDialog.background();
      myDialog = null;
    }
  }

  public void setText(String text) {
    if (!text.equals(getText())) {
      super.setText(text);
      update();
    }
  }

  public void setFraction(double fraction) {
    if (fraction != getFraction()) {
      super.setFraction(fraction);
      update();
    }
  }

  public void setText2(String text) {
    if (!text.equals(getText2())) {
      super.setText2(text);
      update();
    }
  }

  private void update() {
    if (myDialog != null) {
      myDialog.update();
    }
  }

  public void setTitle(String title) {
    if (!Comparing.equal(title, myTitle)) {
      myTitle = title;
      update();
    }
  }

  public String getTitle() {
    return myTitle;
  }

  protected static int getPercentage(double fraction) {
    return (int)(fraction * 99 + 0.5);
  }

  private class MyDialog {
    private long myLastTimeDrawn = -1;
    private boolean myShouldShowCancel;
    private boolean myShouldShowBackground;

    private Runnable myRepaintRunnable = new Runnable() {
      public void run() {
        String text = getText();
        double fraction = getFraction();
        String text2 = getText2();

        myTextLabel.setText(text != null && text.length() > 0 ? text : " ");
        if (!isIndeterminate() && fraction > 0) {
          myPercentLabel.setText(getPercentage(fraction) + "%");
        }
        else {
          myPercentLabel.setText(" ");
        }

        final int perc = (int)(fraction * 100);
        if (perc != myProgressBar.getValue()) {
          if (perc != 0) {
            if (myProgressBar.isIndeterminate()) {
              myProgressBar.setIndeterminate(false);
            }
            myProgressBar.setValue(perc);
          }
          else {
            if (myProgressBar.isIndeterminate()) {
              myProgressBar.setIndeterminate(true);
            }
          }
        }

        myText2Label.setText(getTitle2Text(text2, myText2Label.getWidth()));

        myTitlePanel.setText(myTitle != null && myTitle.length() > 0 ? myTitle : " ");

        myLastTimeDrawn = System.currentTimeMillis();
        myRepaintedFlag = true;
      }
    };

    private String getTitle2Text(String fullText, int labelWidth) {
      if (fullText == null || fullText.length() == 0) return " ";
      while (myText2Label.getFontMetrics(myText2Label.getFont()).stringWidth(fullText) > labelWidth) {
        int sep = fullText.indexOf(File.separatorChar, 4);
        if (sep < 0) return fullText;
        fullText = "..." + fullText.substring(sep);
      }

      return fullText;
    }

    private final Runnable myUpdateRequest = new Runnable() {
      public void run() {
        update();
      }
    };

    private JPanel myPanel;

    private JLabel myTextLabel;
    private JLabel myPercentLabel;
    private JLabel myText2Label;

    private JButton myCancelButton;
    private JButton myBackgroundButton;

    private JProgressBar myProgressBar;
    private boolean myRepaintedFlag = true;
    private JPanel myFunPanel;
    private TitlePanel myTitlePanel;
    private DialogWrapper myPopup;
    private Window myParentWindow;
    private Point myLastClicked;

    public void setIndeterminate (boolean indeterminate) {
      myProgressBar.setIndeterminate(indeterminate);
    }

    public MyDialog(boolean shouldShowCancel, boolean shouldShowBackground, Project project, String cancelText) {
      myParentWindow = WindowManager.getInstance().suggestParentWindow(project);
      if (myParentWindow == null) {
        myParentWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
      }
      
      initDialog(shouldShowCancel, shouldShowBackground, cancelText);
    }

    public MyDialog(boolean shouldShowCancel, boolean shouldShowBackground, Component parent, String cancelText) {
      myParentWindow = parent instanceof Window
                       ? (Window)parent
                       : (Window)SwingUtilities.getAncestorOfClass(Window.class, parent);
      initDialog(shouldShowCancel, shouldShowBackground, cancelText);
    }

    private void initDialog(boolean shouldShowCancel, boolean shouldShowBackground, String cancelText) {
      myFunPanel.setLayout(new BorderLayout());
      myCancelButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doCancelAction();
        }
      });

      myCancelButton.registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (myCancelButton.isEnabled()) {
            doCancelAction();
          }
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

      myShouldShowCancel = shouldShowCancel;
      myShouldShowBackground = shouldShowBackground;
      if (cancelText != null) {
        setCancelButtonText(cancelText);
      }
      myProgressBar.setMaximum(100);
      myProgressBar.setIndeterminate(true);
      createCenterPanel();

      myTitlePanel.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          final Point titleOffset = RelativePoint.getNorthWestOf(myTitlePanel).getScreenPoint();
          myLastClicked = new RelativePoint(e).getScreenPoint();
          myLastClicked.x -= titleOffset.x;
          myLastClicked.y -= titleOffset.y;
        }
      });

      myTitlePanel.addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseDragged(MouseEvent e) {
          if (myLastClicked == null) {
            return;
          }
          final Point draggedTo = new RelativePoint(e).getScreenPoint();
          draggedTo.x -= myLastClicked.x;
          draggedTo.y -= myLastClicked.y;

          final Window wnd = SwingUtilities.getWindowAncestor(myPanel);
          wnd.setLocation(draggedTo);
        }
      });
    }

    public void changeCancelButtonText(String text){
      setCancelButtonText(text);
    }

    protected boolean isProgressDialog() {
      return true;
    }

    public void doCancelAction() {
      if (myShouldShowCancel) {
        ProgressWindow.this.cancel();
      }
    }

    public void cancel() {
      if (myShouldShowCancel) {
        myCancelButton.setEnabled(false);
      }
    }

    protected JComponent createCenterPanel() {
      // Cancel button (if any)

      myCancelButton.setVisible(myShouldShowCancel);

      myBackgroundButton.setVisible(myShouldShowBackground);
      if (myShouldShowBackground) {
        myBackgroundButton.addActionListener(
          new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              ProgressWindow.this.background();
            }
          }
        );
      }

      // Panel with progress indicator and percents

      int width = myPercentLabel.getFontMetrics(myPercentLabel.getFont()).stringWidth("1000%");
      myPercentLabel.setPreferredSize(new Dimension(width, myPercentLabel.getPreferredSize().height));
      myPercentLabel.setHorizontalAlignment(SwingConstants.RIGHT);

      return myPanel;
    }

    private void update() {
      synchronized (this) {
        if (myRepaintedFlag) {
          if (System.currentTimeMillis() > myLastTimeDrawn + UPDATE_INTERVAL) {
            myRepaintedFlag = false;
            SwingUtilities.invokeLater(myRepaintRunnable);
          }
          else {
            if (myUpdateAlarm.getActiveRequestCount() == 0){
              myUpdateAlarm.addRequest(myUpdateRequest, 500, getModalityState());
            }
          }
        }
      }
    }

    public void background() {
      synchronized (this) {
        if (myShouldShowBackground) {
          myBackgroundButton.setEnabled(false);
        }

        hide();
      }
    }

    public void hide() {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (myPopup != null) {
            myPopup.close(DialogWrapper.CANCEL_EXIT_CODE);
            myPopup = null;
          }
        }
      });
    }

    public void show() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
      if (myParentWindow == null) return;
      if (myPopup != null) {
        myPopup.close(DialogWrapper.CANCEL_EXIT_CODE);
      }

      myPopup = myParentWindow.isShowing() ? new MyDialogWrapper(myParentWindow) : new MyDialogWrapper(myProject);
      myPopup.setUndecorated(true);

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myCancelButton.requestFocus();
        }
      });

      myPopup.show();
    }

    public void setTitle(final String title) {

    }

    private class MyDialogWrapper extends DialogWrapper {
      public MyDialogWrapper(Project project) {
        super(project, false);
        init();
      }

      public MyDialogWrapper(Component parent) {
        super(parent, false);
        init();
      }

      protected void init() {
        super.init();
        setUndecorated(true);
        if (!SystemInfo.isMac) {
          myPanel.setBorder(new BlockBorder());
        }
      }

      protected JComponent createCenterPanel() {
        return myPanel;
      }

      @Nullable
      protected JComponent createSouthPanel() {
        return null;
      }

      @Nullable
        protected Border createContentPaneBorder() {
        return null;
      }
    }
  }

  public void setCancelButtonText(String text){
    if (myDialog != null) {
      myDialog.changeCancelButtonText(text);
    }
    else {
      myCancelText = text;
    }
  }

  private void setFunComponent(JComponent c) {
    myDialog.myFunPanel.removeAll();
    if (c != null) {
      myDialog.myFunPanel.add(new JSeparator(), BorderLayout.NORTH);
      myDialog.myFunPanel.add(c, BorderLayout.CENTER);
    }

    final Window wnd = SwingUtilities.windowForComponent(myDialog.myPanel);
    wnd.pack();
  }

  public Project getProject() {
    return myProject;
  }
}
