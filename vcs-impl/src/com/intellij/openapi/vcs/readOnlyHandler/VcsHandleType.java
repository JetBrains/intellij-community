package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.application.ApplicationManager;

import java.util.Collection;

/**
 * @author yole
 */
public class VcsHandleType extends HandleType {
  private final AbstractVcs myVcs;

  public VcsHandleType(AbstractVcs vcs) {
    super(VcsBundle.message("handle.ro.file.status.type.using.vcs", vcs.getDisplayName()), true);
    myVcs = vcs;
  }

  public void processFiles(final Collection<VirtualFile> files) {
    try {
      myVcs.getEditFileProvider().editFiles(files.toArray(new VirtualFile[files.size()]));
    }
    catch (VcsException e) {
      Messages.showErrorDialog(VcsBundle.message("message.text.cannot.edit.file", e.getLocalizedMessage()),
                               VcsBundle.message("message.title.edit.files"));
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (final VirtualFile file : files) {
          file.refresh(false, false);
        }

      }
    });
  }
}