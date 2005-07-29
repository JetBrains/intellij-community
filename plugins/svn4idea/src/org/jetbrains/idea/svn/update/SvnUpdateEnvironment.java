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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnWCRootCrawler;
import org.jetbrains.idea.svn.actions.SvnMergeProvider;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.*;

public class SvnUpdateEnvironment implements UpdateEnvironment {
  private final SvnVcs myVcs;
  private Configurable myConfigurable;

  public SvnUpdateEnvironment(SvnVcs vcs) {
    myVcs = vcs;
  }

  public void fillGroups(UpdatedFiles updatedFiles) {
  }

  public UpdateSession updateDirectories(FilePath[] contentRoots,
                                         final UpdatedFiles updatedFiles,
                                         final ProgressIndicator progressIndicator)
    throws ProcessCanceledException {

    final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
    ISVNEventHandler eventHandler = new UpdateEventHandler(myVcs, progressIndicator, updatedFiles);

    boolean totalUpdate = true;
    UpdateCrawler crawler = new UpdateCrawler(myVcs, eventHandler, totalUpdate, exceptions, updatedFiles);

    Collection updatedRoots = new HashSet();
    for (int i = 0; i < contentRoots.length; i++) {
      if (progressIndicator != null && progressIndicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
      if (contentRoots[i].getIOFile() != null) {
        Collection roots = SvnUtil.crawlWCRoots(myVcs, contentRoots[i].getIOFile(), crawler, progressIndicator);
        updatedRoots.addAll(roots);

      }
    }
    if (updatedRoots.isEmpty()) {
      Messages.showErrorDialog(myVcs.getProject(), "No versioned directories to update was found", "SVN: Update Error");
    }

    SvnUpdateConfigurable config = (SvnUpdateConfigurable)(myConfigurable instanceof SvnUpdateConfigurable ? myConfigurable : null);
    if (config != null && config.isDryRun() && config.isMerge()) {
      return new UpdateSessionAdapter(exceptions, false);
    }

    final Collection conflictedFiles = updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).getFiles();
    return new UpdateSessionAdapter(exceptions, false) {
      public void onRefreshFilesCompleted() {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            if (conflictedFiles != null && !conflictedFiles.isEmpty()) {
              List<VirtualFile> vfFiles = new ArrayList<VirtualFile>();
              for (Iterator paths = conflictedFiles.iterator(); paths.hasNext();) {
                String path = (String)paths.next();
                path = "file://" + path.replace(File.separatorChar, '/');
                VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(path);
                if (vf != null) {
                  vfFiles.add(vf);
                }
              }
              if (!vfFiles.isEmpty()) {
                AbstractVcsHelper.getInstance(myVcs.getProject()).showMergeDialog(vfFiles,
                                                                                  new SvnMergeProvider(myVcs.getProject()),
                                                                                  null);
              }
            }
          }
        }, ModalityState.defaultModalityState());
      }

    };
  }

  public Configurable createConfigurable(Collection<FilePath> collection) {

    SVNInfo info = null;
    if (collection != null && collection.size() == 1) {
      FilePath path = collection.iterator().next();
      File ioFile = path.getIOFile();
      try {
        SVNWCClient wcClient = myVcs.createWCClient();
        info = wcClient.doInfo(ioFile, SVNRevision.WORKING);
      }
      catch (SVNException e) {
        //
      }
    }
    if (info != null && info.getURL() != null) {
      myConfigurable = new SvnUpdateConfigurable(myVcs, info.getURL());
    } else {
      myConfigurable = new SvnSimpleUpdateConfigurable();
    }
    return myConfigurable;
  }

  private static class UpdateEventHandler implements ISVNEventHandler {
    private final ProgressIndicator myProgressIndicator;
    private final UpdatedFiles myUpdatedFiles;
    private int myExternalsCount;
    private SvnVcs myVCS;

    public UpdateEventHandler(SvnVcs vcs, ProgressIndicator progressIndicator, UpdatedFiles updatedFiles) {
      myProgressIndicator = progressIndicator;
      myUpdatedFiles = updatedFiles;
      myVCS = vcs;
      myExternalsCount = 1;
    }

    public void handleEvent(SVNEvent event, double progress) {
      if (event == null || event.getFile() == null) {
        return;
      }
      String path = event.getFile().getAbsolutePath();
      String displayPath = event.getFile().getName();
      if (event.getAction() == SVNEventAction.UPDATE_ADD ||
          event.getAction() == SVNEventAction.ADD) {
        myProgressIndicator.setText2("Added " + displayPath);
        myUpdatedFiles.getGroupById(FileGroup.CREATED_ID).add(path);
      }
      else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
        myProgressIndicator.setText2("Deleted " + displayPath);
        myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).add(path);
      }
      else if (event.getAction() == SVNEventAction.UPDATE_UPDATE) {
        if (event.getContentsStatus() == SVNStatusType.CONFLICTED || event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
          myUpdatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).add(path);
          myProgressIndicator.setText2("Conflicted " + displayPath);
        }
        else if (event.getContentsStatus() == SVNStatusType.MERGED || event.getPropertiesStatus() == SVNStatusType.MERGED) {
          myProgressIndicator.setText2("Merged " + displayPath);
          myUpdatedFiles.getGroupById(FileGroup.MERGED_ID).add(path);
        }
        else if (event.getContentsStatus() == SVNStatusType.CHANGED || event.getPropertiesStatus() == SVNStatusType.CHANGED) {
          myProgressIndicator.setText2("Updated " + displayPath);
          myUpdatedFiles.getGroupById(FileGroup.UPDATED_ID).add(path);
        }
        else {
          myProgressIndicator.setText2("");
          myUpdatedFiles.getGroupById(FileGroup.UNKNOWN_ID).add(path);
        }
      }
      else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
        myExternalsCount++;
        if (myUpdatedFiles.getGroupById("external") == null) {
          myUpdatedFiles.registerGroup(new FileGroup("Externals", "Externals", false, "external", true));
        }
        myUpdatedFiles.getGroupById("external").add(path);
        myProgressIndicator.setText("Updating external location at '" + event.getFile().getAbsolutePath() + "'");
      }
      else if (event.getAction() == SVNEventAction.RESTORE) {
        myProgressIndicator.setText2("Restored " + displayPath);
        myUpdatedFiles.getGroupById(FileGroup.RESTORED_ID).add(path);
      }
      else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED && event.getRevision() >= 0) {
        myExternalsCount--;
        myProgressIndicator.setText2("Updated to revision " + event.getRevision() + ".");
        if (myExternalsCount == 0) {
          myExternalsCount = 1;
          WindowManager.getInstance().getStatusBar(myVCS.getProject()).setInfo("Updated to revision " + event.getRevision() + ".");
        }
      }
      else if (event.getAction() == SVNEventAction.SKIP) {
        myProgressIndicator.setText2("Skipped " + displayPath);
        if (myUpdatedFiles.getGroupById("skip") == null) {
          myUpdatedFiles.registerGroup(new FileGroup("Skipped", "Skipped", false, "skip", true));
        }
        myUpdatedFiles.getGroupById("skip").add(path);
      }
    }

    public void checkCancelled() throws SVNCancelException {
      myProgressIndicator.checkCanceled();
      if (myProgressIndicator.isCanceled()) {
        throw new SVNCancelException("Operation cancelled");
      }
    }
  }

  private class UpdateCrawler implements SvnWCRootCrawler {
    private SvnVcs myVcs;
    private ISVNEventHandler myHandler;
    private Collection myExceptions;
    private UpdatedFiles myPostUpdateFiles;
    private boolean myIsTotalUpdate;

    public UpdateCrawler(SvnVcs vcs, ISVNEventHandler handler, boolean totalUpdate, Collection exceptions, UpdatedFiles postUpdateFiles) {
      myVcs = vcs;
      myHandler = handler;
      myExceptions = exceptions;
      myPostUpdateFiles = postUpdateFiles;
      myIsTotalUpdate = totalUpdate;

    }

    public Collection handleWorkingCopyRoot(File root, ProgressIndicator progress) {
      final Collection result = new HashSet();

      SvnSimpleUpdateConfigurable simpleConfig = (SvnSimpleUpdateConfigurable)(myConfigurable instanceof SvnSimpleUpdateConfigurable ? myConfigurable : null);
      SvnUpdateConfigurable config = (SvnUpdateConfigurable)(myConfigurable instanceof SvnUpdateConfigurable ? myConfigurable : null);

      SVNRevision revision = config != null ? config.getTargetRevision() : SVNRevision.HEAD;
      String url = config != null ? config.getTargetURL() : null;

      long rev;
      boolean recursive = config != null ? config.isRecursive() : (simpleConfig == null || simpleConfig.isRecursive());
      boolean merge = config != null && config.isMerge();
      boolean dryRun = config != null && config.isDryRun();

      if (progress != null) {
        if (merge) {
          if (dryRun) {
            progress.setText("Merging (dry run) changes into '" + root.getAbsolutePath() + "'");
          }
          else {
            progress.setText("Merging changes into '" + root.getAbsolutePath() + "'");
          }
        }
        else {
          progress.setText("Updating '" + root.getAbsolutePath() + "'");
        }
      }
      try {
        SVNUpdateClient client = myVcs.createUpdateClient();
        client.setEventHandler(myHandler);

        if (merge) {
          String url1 = config.getMergeURL1();
          String url2 = config.getMergeURL2();
          SVNRevision rev1 = config.getMergeRevision1();
          SVNRevision rev2 = config.getMergeRevision2();

          rev1 = rev1 == null ? SVNRevision.HEAD : rev1;
          rev2 = rev2 == null ? SVNRevision.HEAD : rev2;
          rev = 0;

          SVNDiffClient diffClient = myVcs.createDiffClient();
          diffClient.setEventHandler(myHandler);
          SVNURL svnURL1 = SVNURL.parseURIEncoded(url1);
          SVNURL svnURL2 = SVNURL.parseURIEncoded(url2);
          diffClient.doMerge(svnURL1, rev1, svnURL2, rev2, root, recursive, true, false, dryRun);
        }
        else if (config != null && !config.isUpdate() && url != null) {
          rev = client.doSwitch(root, url, revision, recursive);
        }
        else {
          rev = client.doUpdate(root, revision, recursive);
        }
        if (rev < 0) {
          throw new SVNException(
            "svn: " + root + " was not properly updated; may be it is already removed from the repository along with its parent.");
        }
      }
      catch (SVNException e) {
        myExceptions.add(new VcsException(e));
      }
      boolean runStatus = config != null ? config.isRunStatus() :
                          (simpleConfig != null && simpleConfig.isRunStatus());
      if (!runStatus) {
        return result;
      }

      try {
        SVNStatusClient statusClient = myVcs.createStatusClient();
        statusClient.setIgnoreExternals(false);

        if (progress != null) {
          progress.setText("Computing post-update status of '" + root.getAbsolutePath() + "'");
        }
        statusClient.doStatus(root, true, false, false, false, new ISVNStatusHandler() {
          public void handleStatus(SVNStatus status) {
            if (status.getFile() == null) {
              return;
            }
            if (myIsTotalUpdate &&
                status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED &&
                status.getFile().isDirectory()) {
              result.add(status.getFile());
            }
            if (status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL ||
                status.getContentsStatus() == SVNStatusType.STATUS_IGNORED ||
                status.getContentsStatus() == SVNStatusType.STATUS_MISSING ||
                status.getContentsStatus() == SVNStatusType.STATUS_INCOMPLETE ||
                status.getContentsStatus() == SVNStatusType.STATUS_MISSING) {
              // not interesting in post-update.
            }
            else if (status.getContentsStatus() != SVNStatusType.STATUS_NONE || status.getPropertiesStatus() == SVNStatusType.STATUS_NONE) {
              String path = status.getFile().getAbsolutePath();

              if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
                myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_ADDED_ID).add(path);
              }
              else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED) {
                  // may conflict with update status.
                FileGroup group = myPostUpdateFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID);
                if (group != null && (group.getFiles() == null || !group.getFiles().contains(path))) {
                  group.add(path);
                }
              }
              else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
                myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_REMOVED_ID).add(path);
              }
              else if (status.getContentsStatus() == SVNStatusType.STATUS_REPLACED) {
                myPostUpdateFiles.getGroupById(FileGroup.LOCALLY_ADDED_ID).add(path);
              }
              else if (status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED ||
                       status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
                myPostUpdateFiles.getGroupById(FileGroup.MODIFIED_ID).add(path);
              }
              else if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                       status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
                if (status.getFile().isFile() || !SVNWCUtil.isVersionedDirectory(status.getFile())) {
                  myPostUpdateFiles.getGroupById(FileGroup.UNKNOWN_ID).add(path);
                }
              }
            }
          }
        });
      }
      catch (SVNException e) {
        myExceptions.add(new VcsException(e));
      }
      return result;
    }
  }
}