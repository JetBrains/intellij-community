package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public class ToggleDumbModeAction extends AnAction implements DumbAware {
  private volatile boolean myDumb = false;

  public void actionPerformed(final AnActionEvent e) {
    if (myDumb) {
      myDumb = false;
    } else {
      myDumb = true;
      final Project project = DataKeys.PROJECT.getData(e.getDataContext());
      if (project == null) return;

      DumbServiceImpl.getInstance(project).queueIndexUpdate(new Consumer<ProgressIndicator>() {
        public void consume(ProgressIndicator progressIndicator) {
          while (myDumb) {
            try {
              Thread.sleep(100);
            }
            catch (InterruptedException e1) {
            }
          }
        }
      });
    }
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = DataKeys.PROJECT.getData(e.getDataContext());
    presentation.setEnabled(project != null && myDumb == DumbServiceImpl.getInstance(project).isDumb());
    if (myDumb) {
      presentation.setText("Exit dumb mode");
    } else {
      presentation.setText("Enter dumb mode");
    }
  }

}