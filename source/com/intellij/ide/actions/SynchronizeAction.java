
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFileManager;

public class SynchronizeAction extends AnAction {
  private final boolean myAsynchronous;

  public SynchronizeAction() {
    this(true);
  }

  public SynchronizeAction(boolean asynchronous) {
    myAsynchronous = asynchronous;
  }

  public void actionPerformed(AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    final VirtualFileManager vfMan = VirtualFileManager.getInstance();
    Runnable synchronizeRunnable = new Runnable() {
      public void run() {
        vfMan.refresh(myAsynchronous);
      }
    };
    if (myAsynchronous) {
      ApplicationManager.getApplication().runReadAction(synchronizeRunnable);
    }
    else {
      ApplicationManager.getApplication().runWriteAction(synchronizeRunnable);
    }
  }
}
