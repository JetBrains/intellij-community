/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 20, 2006
 * Time: 8:40:15 PM
 */
package com.intellij.find;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BackgroundableProcessIndicator extends ProgressWindow {
  protected final StatusBarEx myStatusBar;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
  private volatile boolean myIsBackground;
  protected final String myBackgroundStopButtonTooltip;
  private PerformInBackgroundOption myOption;

  public BackgroundableProcessIndicator(Project project,
                                        @Nls String progressTitle,
                                        @NotNull PerformInBackgroundOption option,
                                        @Nls String cancelButtonText,
                                        @Nls String backgroundStopTooltip) {
    super (true, true, project, cancelButtonText);
    myOption = option;
    setTitle(progressTitle);
    myStatusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
    if (option.shouldStartInBackground()) {
      doBackground();
    }

    myBackgroundStopButtonTooltip = backgroundStopTooltip;
  }

  protected void showDialog() {
    if (myOption.shouldStartInBackground()) {
      return;
    }

    super.showDialog();
  }

  public void background() {
    myOption.processSentToBackground();
    doBackground();
    super.background();
  }

  protected void doBackground() {
    myIsBackground = true;
    myStatusBar.addProgress();
    myStatusBar.showCancelButton(
      IconLoader.getIcon("/actions/suspend.png"),
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          cancel();
        }
      }, myBackgroundStopButtonTooltip);
  }

  public void setText(String text) {
    if (!myIsBackground) {
      super.setText(text);
    }
    else {
      myStatusBar.setInfo(getTitle() + ": " + text);
    }
  }

  public void setFraction(double fraction) {
    if (!myIsBackground) {
      super.setFraction(fraction);
    }
    else {
      myStatusBar.setProgressValue(getPercentage(fraction));
    }
  }

  public void setText2(String text) {
    if (!myIsBackground) super.setText2(text);
  }

  public void cancel() {
    if (myIsBackground) myStatusBar.hideProgress();
    super.cancel();
  }

  public synchronized void stop() {
    if (myIsBackground) {
      myStatusBar.hideCancelButton();
    }
    super.stop();
  }
}