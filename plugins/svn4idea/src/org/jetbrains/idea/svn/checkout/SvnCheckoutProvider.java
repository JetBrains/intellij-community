/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.CheckoutDialog;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;

public class SvnCheckoutProvider implements CheckoutProvider {

  public void doCheckout() {
    final Project project = ProjectManager.getInstance().getDefaultProject();
    CheckoutDialog dialog = new CheckoutDialog(project);
    dialog.show();
    if (!dialog.isOK() || dialog.getSelectedURL() == null || dialog.getSelectedFile() == null) {
      return;
    }

    try {
      final SVNException[] exception = new SVNException[1];
      final SVNUpdateClient client = SvnVcs.getInstance(project).createUpdateClient();
      final String url = dialog.getSelectedURL();
      final File target = new File(dialog.getSelectedFile());

      ApplicationManager.getApplication().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), progressIndicator));
          try {
            progressIndicator.setText("Checking out files to '" + target.getAbsolutePath() + "'");
            client.doCheckout(url, target, SVNRevision.UNDEFINED, SVNRevision.HEAD, true);
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          finally {
            client.setEventHandler(null);
          }
        }
      }, "Check Out from Subversion", false, null);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch (SVNException e1) {
      Messages.showErrorDialog("Cannot checkout from svn: " + e1.getMessage(), "Check Out from Subversion");
    }
  }

  public String getVcsName() {
    return "_SVN";
  }

  public String getComponentName() {
    return "SvnCheckoutProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private static class CheckoutEventHandler implements ISVNEventHandler {
    private ProgressIndicator myIndicator;
    private int myExternalsCount;
    private SvnVcs myVCS;

    public CheckoutEventHandler(SvnVcs vcs, ProgressIndicator indicator) {
      myIndicator = indicator;
      myVCS = vcs;
      myExternalsCount = 1;
    }

    public void handleEvent(SVNEvent event, double progress) {
      if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
        myExternalsCount++;
        myIndicator.setText("Fetching external location to '" + event.getFile().getAbsolutePath() + "'");
        myIndicator.setText2("");
      }
      else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
        myIndicator.setText2("Checked out " + event.getFile().getName());
      }
      else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {
        myExternalsCount--;
        myIndicator.setText2("Checked out revision " + event.getRevision() + ".");
        if (myExternalsCount == 0 && event.getRevision() >= 0 && myVCS != null) {
          myExternalsCount = 1;
          Project project = myVCS.getProject();
          if (project != null) {
            StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
            if (statusBar != null) {
              statusBar.setInfo("Checked out revision " + event.getRevision() + ".");
            }
          }
        }

      }
    }

    public void checkCancelled() throws SVNCancelException {
      myIndicator.checkCanceled();
      if (myIndicator.isCanceled()) {
        throw new SVNCancelException("");
      }
    }
  }
}


