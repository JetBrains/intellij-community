package org.jetbrains.idea.svn.update;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnFileUrlMapping;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;

/**
 * @author lesya
*/
public class UpdateEventHandler implements ISVNEventHandler {
  private final ProgressIndicator myProgressIndicator;
  private UpdatedFiles myUpdatedFiles;
  private int myExternalsCount;
  private final SvnVcs myVCS;

  protected String myText;
  protected String myText2;

  public UpdateEventHandler(SvnVcs vcs, ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
    myVCS = vcs;
    myExternalsCount = 1;
  }

  public void setUpdatedFiles(final UpdatedFiles updatedFiles) {
    myUpdatedFiles = updatedFiles;
  }

  public void handleEvent(SVNEvent event, double progress) {
    if (event == null || event.getFile() == null) {
      return;
    }

    String path = event.getFile().getAbsolutePath();
    String displayPath = event.getFile().getName();
    myText2 = null;
    myText = null;

    if (handleInDescendants(event)) {
      updateProgressIndicator();
      return;
    }

    if (event.getAction() == SVNEventAction.UPDATE_ADD ||
        event.getAction() == SVNEventAction.ADD) {
      myText2 = SvnBundle.message("progress.text2.added", displayPath);
      if (event.getContentsStatus() == SVNStatusType.CONFLICTED || event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
        addFileToGroup(FileGroup.MERGED_WITH_CONFLICT_ID, event);
        myText2 = SvnBundle.message("progress.text2.conflicted", displayPath);
      } else if (myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).getFiles().contains(path)) {
        myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).getFiles().remove(path);
        if (myUpdatedFiles.getGroupById(AbstractSvnUpdateIntegrateEnvironment.REPLACED_ID) == null) {
          myUpdatedFiles.registerGroup(createFileGroup(SvnBundle.message("status.group.name.replaced"),
              AbstractSvnUpdateIntegrateEnvironment.REPLACED_ID));
        }
        addFileToGroup(AbstractSvnUpdateIntegrateEnvironment.REPLACED_ID, event);
      } else {
        addFileToGroup(FileGroup.CREATED_ID, event);
      }
    }
    else if (event.getAction() == SVNEventAction.UPDATE_NONE) {
      // skip it
      return;
    }
    else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
      myText2 = SvnBundle.message("progress.text2.deleted", displayPath);
      addFileToGroup(FileGroup.REMOVED_FROM_REPOSITORY_ID, event);
    }
    else if (event.getAction() == SVNEventAction.UPDATE_UPDATE) {
      if (event.getContentsStatus() == SVNStatusType.CONFLICTED || event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
        addFileToGroup(FileGroup.MERGED_WITH_CONFLICT_ID, event);
        myText2 = SvnBundle.message("progress.text2.conflicted", displayPath);
      }
      else if (event.getContentsStatus() == SVNStatusType.MERGED || event.getPropertiesStatus() == SVNStatusType.MERGED) {
        myText2 = SvnBundle.message("progres.text2.merged", displayPath);
        addFileToGroup(FileGroup.MERGED_ID, event);
      }
      else if (event.getContentsStatus() == SVNStatusType.CHANGED || event.getPropertiesStatus() == SVNStatusType.CHANGED) {
        myText2 = SvnBundle.message("progres.text2.updated", displayPath);
        addFileToGroup(FileGroup.UPDATED_ID, event);
      }
      else if (event.getContentsStatus() == SVNStatusType.UNCHANGED &&
               (event.getPropertiesStatus() == SVNStatusType.UNCHANGED || event.getPropertiesStatus() == SVNStatusType.UNKNOWN)) {
        myText2 = SvnBundle.message("progres.text2.updated", displayPath);
      }
      else {
        myText2 = "";
        addFileToGroup(FileGroup.UNKNOWN_ID, event);
      }
    }
    else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
      myExternalsCount++;
      if (myUpdatedFiles.getGroupById(AbstractSvnUpdateIntegrateEnvironment.EXTERNAL_ID) == null) {
        myUpdatedFiles.registerGroup(new FileGroup(SvnBundle.message("status.group.name.externals"),
                                                   SvnBundle.message("status.group.name.externals"),
                                                   false, AbstractSvnUpdateIntegrateEnvironment.EXTERNAL_ID, true));
      }
      addFileToGroup(AbstractSvnUpdateIntegrateEnvironment.EXTERNAL_ID, event);
      myText = SvnBundle.message("progress.text.updating.external.location", event.getFile().getAbsolutePath());
    }
    else if (event.getAction() == SVNEventAction.RESTORE) {
      myText2 = SvnBundle.message("progress.text2.restored.file", displayPath);
      addFileToGroup(FileGroup.RESTORED_ID, event);
    }
    else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED && event.getRevision() >= 0) {
      myExternalsCount--;
      myText2 = SvnBundle.message("progres.text2.updated.to.revision", event.getRevision());
      if (myExternalsCount == 0) {
        myExternalsCount = 1;
        WindowManager.getInstance().getStatusBar(myVCS.getProject()).setInfo(
          SvnBundle.message("status.text.updated.to.revision", event.getRevision()));
      }
    }
    else if (event.getAction() == SVNEventAction.SKIP) {
      myText2 = SvnBundle.message("progress.text2.skipped.file", displayPath);
      addFileToGroup(FileGroup.SKIPPED_ID, event);
    }

    updateProgressIndicator();
  }

  private boolean itemSwitched(final SVNEvent event) {
    final File file = event.getFile();
    final SvnFileUrlMapping urlMapping = myVCS.getSvnFileUrlMapping();
    final SVNURL currentUrl = urlMapping.getUrlForFile(file);
    return (currentUrl != null) && (! currentUrl.equals(event.getURL()));
  }

  private void updateProgressIndicator() {
    if (myProgressIndicator != null) {
      if (myText != null) {
        myProgressIndicator.setText(myText);
      }
      if (myText2 != null) {
        myProgressIndicator.setText2(myText2);
      }
    }
  }

  protected boolean handleInDescendants(final SVNEvent event) {
    return false;
  }

  protected void addFileToGroup(final String id, final SVNEvent event) {
    myUpdatedFiles.getGroupById(id).add(event.getFile().getAbsolutePath());
  }

  public void checkCancelled() throws SVNCancelException {
    if (myProgressIndicator != null) {
      myProgressIndicator.checkCanceled();
      if (myProgressIndicator.isCanceled()) {
        SVNErrorManager.cancel(SvnBundle.message("exception.text.update.operation.cancelled"), SVNLogType.DEFAULT);
      }
    }
  }

  private static FileGroup createFileGroup(String name, String id) {
    return new FileGroup(name, name, false, id, true);
  }
}
