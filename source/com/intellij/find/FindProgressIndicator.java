package com.intellij.find;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ex.StatusBarEx;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author ven
 */
public class FindProgressIndicator extends ProgressWindow {
  private StatusBarEx myStatusBar;
  private boolean myIsBackground;

  public FindProgressIndicator(Project project, String scopeString) {
    super (true, true, project, "Stop");
    setTitle("Searching in "+scopeString+" ...");
    myStatusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
  }

  public void background() {
    myIsBackground = true;
    myStatusBar.addProgress();
    myStatusBar.showCancelButton(
      IconLoader.getIcon("/actions/suspend.png"),
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          cancel();
        }
      },
      "Stop background search"
    );
    super.background();
  }

  public void setText(String text) {
    if (!myIsBackground) {
      super.setText(text);
    }
    else {
      myStatusBar.setInfo(text);
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
