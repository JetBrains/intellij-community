package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;

import javax.swing.*;

public class SmoothProgressAdapter extends BlockingProgressIndicator {
  private static final int SHOW_DELAY = 500;

  private Alarm myStartupAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  protected ProgressIndicator myOriginal;
  private Project myProject;

  private boolean myOriginalStarted;

  private DialogWrapper myDialog;

  private final Runnable myShowRequest = new Runnable() {
    public void run() {
      synchronized(SmoothProgressAdapter.this){
        if (!isRunning()) {
          return;
        }

        myOriginal.start();
        myOriginalStarted = true;

        myOriginal.setText(getText());
        myOriginal.setFraction(getFraction());
        myOriginal.setText2(getText2());
      }
    }
  };

  public SmoothProgressAdapter(ProgressIndicator original, Project project){
    myOriginal = original;
    myProject = project;
    if (myOriginal.isModal()) {
      myOriginal.setModalityProgress(this);
      this.setModalityProgress(this);
    }
  }

  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    myOriginal.setIndeterminate(indeterminate);
  }

  public boolean isIndeterminate() {
    return myOriginal.isIndeterminate();
  }

  public synchronized void start() {
    if (isRunning()) return;

    super.start();
    myOriginalStarted = false;
    myStartupAlarm.addRequest(myShowRequest, SHOW_DELAY);
  }

  public void startBlocking() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    start();
    if (isModal()) {
      showDialog();
    }
  }

  private void showDialog(){
    if (myDialog == null){
      //System.out.println("showDialog()");
      myDialog = new DialogWrapper(myProject, false) {
        {
          getWindow().setBounds(0, 0, 1, 1);
          setResizable(false);
        }

        protected boolean isProgressDialog() {
          return true;
        }

        protected JComponent createCenterPanel() {
          return null;
        }
      };
      myDialog.setModal(true);
      myDialog.setUndecorated(true);
      myDialog.show();
    }
  }

  public synchronized void stop() {
    if (myOriginal.isRunning()) {
      myOriginal.stop();
    }
    else {
      myStartupAlarm.cancelAllRequests();
    }

    // needed only for correct assertion of !progress.isRunning() in ApplicationImpl.runProcessWithProgressSynchroniously
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    SwingUtilities.invokeLater(
      new Runnable() {
        public void run() {
          semaphore.waitFor();
          if (myDialog != null){
            //System.out.println("myDialog.destroyProcess()");
            myDialog.close(DialogWrapper.OK_EXIT_CODE);
            myDialog = null;
          }
        }
      }
    );

    super.stop(); // should be last to not leaveModal before closing the dialog
    semaphore.up();
  }

  public synchronized void setText(String text) {
    super.setText(text);
    if (myOriginal.isRunning()) {
      myOriginal.setText(text);
    }
  }

  public synchronized void setFraction(double fraction) {
    super.setFraction(fraction);
    if (myOriginal.isRunning()) {
      myOriginal.setFraction(fraction);
    }
  }
  
  public synchronized void setText2(String text) {
    super.setText2(text);
    if (myOriginal.isRunning()) {
      myOriginal.setText2(text);
    }
  }

  public void cancel() {
    super.cancel();
    myOriginal.cancel();
  }



  public boolean isCanceled() {
    if (super.isCanceled()) return true;
    if (!myOriginalStarted) return false;
    return myOriginal.isCanceled();
  }

  public ProgressIndicator getOriginal() {
    return myOriginal;
  }

}
