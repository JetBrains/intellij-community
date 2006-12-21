/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 20, 2006
 * Time: 8:40:15 PM
 */
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProcessInfo;
import com.intellij.openapi.wm.ex.StatusBarEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class BackgroundableProcessIndicator extends ProgressWindow {
  protected final StatusBarEx myStatusBar;

  @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})

  protected final String myBackgroundStopButtonTooltip;
  private PerformInBackgroundOption myOption;

  public BackgroundableProcessIndicator(Project project,
                                        @Nls String progressTitle,
                                        @NotNull PerformInBackgroundOption option,
                                        @Nls String cancelButtonText,
                                        @Nls String backgroundStopTooltip) {
    super (true, true, project, cancelButtonText);
    myBackgroundStopButtonTooltip = backgroundStopTooltip;
    myOption = option;
    setTitle(progressTitle);
    myStatusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
    if (option.shouldStartInBackground()) {
      doBackground();
    }

  }


  public String getBackgroundStopButtonTooltip() {
    return myBackgroundStopButtonTooltip;
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
    myStatusBar.add(this, new ProcessInfo(myBackgroundStopButtonTooltip));
  }
}