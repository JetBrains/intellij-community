/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.Nullable;
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
  private ProgressIndicator myProgressIndicator;
  private UpdatedFiles myUpdatedFiles;
  private int myExternalsCount;
  private final SvnVcs myVCS;
  @Nullable private final SvnUpdateContext mySequentialUpdatesContext;

  protected String myText;
  protected String myText2;

  public UpdateEventHandler(SvnVcs vcs, ProgressIndicator progressIndicator,
                            @Nullable final SvnUpdateContext sequentialUpdatesContext) {
    myProgressIndicator = progressIndicator;
    myVCS = vcs;
    mySequentialUpdatesContext = sequentialUpdatesContext;
    myExternalsCount = 1;
  }

  public void setUpdatedFiles(final UpdatedFiles updatedFiles) {
    myUpdatedFiles = updatedFiles;
  }

  public void handleEvent(final SVNEvent event, double progress) {
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

    if (event.getAction() == SVNEventAction.TREE_CONFLICT) {
      myText2 = SvnBundle.message("progress.text2.treeconflicted", displayPath);
      updateProgressIndicator();
      myUpdatedFiles.registerGroup(createFileGroup(VcsBundle.message("update.group.name.merged.with.tree.conflicts"),
                                                   FileGroup.MERGED_WITH_TREE_CONFLICT));
      addFileToGroup(FileGroup.MERGED_WITH_TREE_CONFLICT, event);
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
        if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
          addFileToGroup(FileGroup.MERGED_WITH_CONFLICT_ID, event);
        }
        if (event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
          addFileToGroup(FileGroup.MERGED_WITH_PROPERTY_CONFLICT_ID, event);
        }
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
      if (mySequentialUpdatesContext != null) {
        mySequentialUpdatesContext.registerExternalRootBeingUpdated(event.getFile());
      }
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
        StatusBar.Info.set(SvnBundle.message("status.text.updated.to.revision", event.getRevision()), myVCS.getProject());
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
    final FileGroup fileGroup = myUpdatedFiles.getGroupById(id);
    final String path = event.getFile().getAbsolutePath();
    fileGroup.add(path, SvnVcs.getKey(), null);
    if (event.getErrorMessage() != null) {
      fileGroup.addError(path, event.getErrorMessage().getMessage());
    }
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

  public void setProgressIndicator(ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
  }
}
