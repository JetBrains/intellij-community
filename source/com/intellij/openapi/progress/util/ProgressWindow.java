package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.Alarm;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProgressWindow extends BlockingProgressIndicator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressWindow");

  private static final int UPDATE_INTERVAL = 50; //msec. 20 frames per second.

  private MyDialog myDialog;
  private Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private Alarm myInstallFunAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private final Project myProject;
  private final boolean myShouldShowBackground;
  private final boolean myShouldShowCancel;
  private String myCancelText;
  private JComponent myParentComponent;

  private String myDeferredTitle = null;

  private boolean myStoppedAlready = false;

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
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, Project project, JComponent parentComponent, String cancelText) {
    myShouldShowBackground = shouldShowBackground;
    myProject = project;
    myShouldShowCancel = shouldShowCancel;
    myCancelText = cancelText;
    myParentComponent = parentComponent;
    setModalityProgress(myShouldShowBackground ? null : this);
  }

  public synchronized void start() {
    LOG.assertTrue(!isRunning());
    LOG.assertTrue(!myStoppedAlready);

    super.start();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myDialog == null && isRunning()) {
            showDialog();
          }
        }
      }, getModalityState());
  }

  public void startBlocking() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(!isRunning());
    LOG.assertTrue(!myStoppedAlready);

    synchronized(this){
      super.start();
    }

    showDialog();
  }

  private void showDialog() {
    //System.out.println("ProgressWindow.showDialog");
    LOG.assertTrue(myDialog == null);
    if(myParentComponent != null) {
      myDialog = new MyDialog(myShouldShowCancel, myShouldShowBackground, myParentComponent, myCancelText);
    } else {
      myDialog = new MyDialog(myShouldShowCancel, myShouldShowBackground, myProject, myCancelText);
    }
    if (myDeferredTitle != null) {
      setTitle(myDeferredTitle);
    }

    final JComponent cmp = ProgressManager.getInstance().getProvidedFunComponent(myProject, "<unknown>");
    if (cmp != null) {
      Runnable installer = new Runnable() {
        public void run() {
          if (isRunning() && !isCanceled() && getFraction() < 0.15 && myDialog!=null) {
            setFunComponent(cmp);
          }
        }
      };
      myInstallFunAlarm.addRequest(installer, 3000, getModalityState());
    }

    myDialog.setIndeterminate(isIndeterminate());
    myDialog.show();
  }

  public synchronized void stop() {
    LOG.assertTrue(!myStoppedAlready);
    myInstallFunAlarm.cancelAllRequests();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myDialog != null) {
            myDialog.close(DialogWrapper.CANCEL_EXIT_CODE);
          }
        }
      }, getModalityState());

    super.stop();
    myStoppedAlready = true;
  }

  public void cancel() {
    super.cancel();
    SwingUtilities.invokeLater(
      new Runnable() {
        public void run() {
          if (myDialog != null) {
            myDialog.cancel();
          }
        }
      }
    );
  }

  public void background() {
    SwingUtilities.invokeLater(
      new Runnable() {
        public void run() {
          if (myDialog != null) {
            myDialog.background();
            myDialog = null;
          }
        }
      }
    );
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
    if (myDialog == null) {
      myDeferredTitle = title;
    }
    else {
      myDialog.setTitle(title);
    }
  }

  protected static final int getPercentage(double fraction) {
    return (int)(fraction * 99 + 0.5);
  }

  private class MyDialog extends DialogWrapper {
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
        myProgressBar.setFraction(fraction);
        myText2Label.setText(text2 != null && text2.length() > 0 ? text2 : " ");

        myLastTimeDrawn = System.currentTimeMillis();
        myRepaintedFlag = true;
      }
    };
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

    private ColorProgressBar myProgressBar;
    private boolean myRepaintedFlag = false;
    private JPanel myFunPanel;

    public void setIndeterminate (boolean indeterminate) {
      myProgressBar.setIndeterminate(indeterminate);
    }

    public MyDialog(boolean shouldShowCancel, boolean shouldShowBackground, Project project, String cancelText) {
      super(project, false);

      initDialog(shouldShowCancel, shouldShowBackground, cancelText);

    }

    public MyDialog(boolean shouldShowCancel, boolean shouldShowBackground, Component parent, String cancelText) {
      super(parent, false);
      initDialog(shouldShowCancel, shouldShowBackground, cancelText);
    }

    private void initDialog(boolean shouldShowCancel, boolean shouldShowBackground, String cancelText) {
      myFunPanel.setLayout(new BorderLayout());
      myCancelButton.setAction(getCancelAction());

      myShouldShowCancel = shouldShowCancel;
      myShouldShowBackground = shouldShowBackground;
      if (cancelText != null) {
        setCancelButtonText(cancelText);
      }
      init();
    }

    public void changeCancelButtonText(String text){
      setCancelButtonText(text);
    }

    protected boolean isProgressDialog() {
      return true;
    }

    protected void init() {
      setCrossClosesWindow(myShouldShowCancel);

      super.init();

      myRepaintRunnable.run();
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

    protected Border createContentPaneBorder() {
      return null;//new LineBorder(Color.BLACK);
    }

    protected JComponent createSouthPanel() {
      return null;
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

        close(DialogWrapper.CANCEL_EXIT_CODE);
      }
    }
  }

  public void setCancelButtonText(String text){
    if (myDialog != null)
      myDialog.changeCancelButtonText(text);
    else
      myCancelText = text;
  }

  private void setFunComponent(JComponent c) {
    myDialog.myFunPanel.removeAll();
    if (c != null) {
      myDialog.myFunPanel.add(new JSeparator(), BorderLayout.NORTH);
      myDialog.myFunPanel.add(c, BorderLayout.CENTER);
    }
    myDialog.pack();
    myDialog.centerRelativeToParent();
  }

  public Project getProject() {
    return myProject;
  }
}
