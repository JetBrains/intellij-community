
package com.intellij.ide.actions;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.util.concurrency.Semaphore;

public class SynchronizeAction extends AnAction {
  private final boolean myAsynchronous;

  public SynchronizeAction() {
    this(false);
  }

  public SynchronizeAction(boolean asynchronous) {
    myAsynchronous = asynchronous;
  }

  public void actionPerformed(AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final VirtualFileManager vfMan = VirtualFileManager.getInstance();
    final Runnable synchronizeRunnable = new Runnable() {
      public void run() {
        vfMan.refresh(myAsynchronous);
      }
    };
    final ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    if (myAsynchronous) {
      application.runReadAction(synchronizeRunnable);
    }
    else {
      final Project project = (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT);
      application.runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
          pi.setText("Synchronizing files...");
          application.invokeAndWait(new Runnable() {
            public void run() {
              application.runWriteAction(synchronizeRunnable);
            }
          }, pi.getModalityState());
        }
      }, "", false, project);
    }
  }
}
