package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.PerformInBackgroundOption;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddTestProcessActionIndefinite extends AnAction {
  public AddTestProcessActionIndefinite() {
    super("Add Test Process");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final BackgroundableProcessIndicator indicator =
      new BackgroundableProcessIndicator(project, "Test process", new PerformInBackgroundOption() {
        public boolean shouldStartInBackground() {
          return true;
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
          countTo(900, new AddTestProcessActionIndefinite.Count() {
            public void onCount(int each) throws InterruptedException {
              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
              }
              sleep(300);
              indicator.checkCanceled();
              indicator.setText2("bla bla bla");
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

  private void countTo(int top, AddTestProcessActionIndefinite.Count count) throws Exception {
    for (int i = 0; i < top; i++) {
      count.onCount(i);
    }
  }

  private static interface Count {
    void onCount(int each) throws InterruptedException;
  }
}
