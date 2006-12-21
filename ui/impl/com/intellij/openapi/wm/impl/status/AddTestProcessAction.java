package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddTestProcessAction extends AnAction {
  public AddTestProcessAction() {
    super("Add Test Process");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final BackgroundableProcessIndicator indicator =
      new BackgroundableProcessIndicator(project, "Test process", new PerformInBackgroundOption() {
        public boolean shouldStartInBackground() {
          return false;
        }

        public void processSentToBackground() {

        }

        public void processRestoredToForeground() {

        }
      }, "Cancel", "Cancel");

    indicator.start();
    new Thread() {
      public void run() {
        try {
          countTo(900, new Count() {
            public void onCount(int each) throws InterruptedException {
              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
              }
              indicator.setFraction(each / 100.0);
              sleep(75);
              indicator.checkCanceled();
            }
          });
          indicator.stop();
        }
        catch (Exception e1) {
          indicator.stop();
          return;
        }
      }
    }.start();

  }

  private void countTo(int top, Count count) throws Exception {
    for (int i = 0; i < top; i++) {
      count.onCount(i);
    }
  }

  private static interface Count {
    void onCount(int each) throws InterruptedException;
  }
}
