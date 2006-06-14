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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.CheckoutDialog;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;

public class SvnCheckoutProvider implements CheckoutProvider {

  public void doCheckout() {
    final Project project = ProjectManager.getInstance().getDefaultProject();
    CheckoutDialog dialog = new CheckoutDialog(project);
    dialog.show();
  }

  public static void doCheckout(final Project project, final File target, final String url, final boolean recursive,
                                final boolean ignoreExternals) {
    try {
      final SVNException[] exception = new SVNException[1];
      final SVNUpdateClient client = SvnVcs.getInstance(project).createUpdateClient();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), false, progressIndicator));
          client.setIgnoreExternals(ignoreExternals);
          try {
            progressIndicator.setText(SvnBundle.message("progress.text.checking.out", target.getAbsolutePath()));
            client.doCheckout(SVNURL.parseURIEncoded(url), target, SVNRevision.UNDEFINED, SVNRevision.HEAD, recursive);
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          finally {
            client.setIgnoreExternals(false);
            client.setEventHandler(null);
          }
        }
      }, SvnBundle.message("message.title.check.out"), true, project);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch (SVNException e1) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.checkout", e1.getMessage()), SvnBundle.message("message.title.check.out"));
    }
  }

  public static void doExport(final Project project, final File target, final String url, final boolean recursive,
                              final boolean ignoreExternals, final boolean force, final String eolStyle) {
    try {
      final SVNException[] exception = new SVNException[1];
      final SVNUpdateClient client = SvnVcs.getInstance(project).createUpdateClient();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), true, progressIndicator));
          client.setIgnoreExternals(ignoreExternals);
          try {
            progressIndicator.setText(SvnBundle.message("progress.text.export", target.getAbsolutePath()));
            client.doExport(SVNURL.parseURIEncoded(url), target, SVNRevision.UNDEFINED, SVNRevision.HEAD, eolStyle, force, recursive);
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          finally {
            client.setIgnoreExternals(false);
            client.setEventHandler(null);
          }
        }
      }, SvnBundle.message("message.title.export"), true, project);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch (SVNException e1) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.export", e1.getMessage()), SvnBundle.message("message.title.export"));
    }
  }

  public static void doImport(final Project project, final File target, final SVNURL url, final boolean recursive,
                              final boolean includeIgnored, final String message) {
    try {
      final SVNException[] exception = new SVNException[1];
      final SVNCommitClient client = SvnVcs.getInstance(project).createCommitClient();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), true, progressIndicator));
          try {
            progressIndicator.setText(SvnBundle.message("progress.text.import", target.getAbsolutePath()));
            client.doImport(target, url, message, !includeIgnored, recursive);
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          finally {
            client.setIgnoreExternals(false);
            client.setEventHandler(null);
          }
        }
      }, SvnBundle.message("message.title.import"), true, project);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch (SVNException e1) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.import", e1.getMessage()), SvnBundle.message("message.title.import"));
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
    private boolean myIsExport;

    public CheckoutEventHandler(SvnVcs vcs, boolean isExport, ProgressIndicator indicator) {
      myIndicator = indicator;
      myVCS = vcs;
      myExternalsCount = 1;
      myIsExport = isExport;
    }

    public void handleEvent(SVNEvent event, double progress) {
      String path = event.getFile() != null ? event.getFile().getName() : event.getPath();
      if (path == null) {
        return;
      }
      if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
        myExternalsCount++;
        myIndicator.setText(SvnBundle.message("progress.text2.fetching.external.location", event.getFile().getAbsolutePath()));
        myIndicator.setText2("");
      }
      else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
        myIndicator.setText2(SvnBundle.message(myIsExport ? "progress.text2.exported" : "progress.text2.checked.out", event.getFile().getName()));
      }
      else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {
        myExternalsCount--;
        myIndicator.setText2(SvnBundle.message(myIsExport ? "progress.text2.exported.revision" : "progress.text2.checked.out.revision", event.getRevision()));
        if (myExternalsCount == 0 && event.getRevision() >= 0 && myVCS != null) {
          myExternalsCount = 1;
          Project project = myVCS.getProject();
          if (project != null) {
            StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
            if (statusBar != null) {
              statusBar.setInfo(SvnBundle.message(myIsExport ? "progress.text2.exported.revision" : "status.text.checked.out.revision", event.getRevision()));
            }
          }
        }
      } else if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
        myIndicator.setText2(SvnBundle.message("progress.text2.adding", path));
      } else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
        myIndicator.setText2(SvnBundle.message("progress.text2.transmitting.delta", path));
      }
    }

    public void checkCancelled() throws SVNCancelException {
      myIndicator.checkCanceled();
      if (myIndicator.isCanceled()) {
        throw new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, "Operation cancelled"));
      }
    }
  }
}


