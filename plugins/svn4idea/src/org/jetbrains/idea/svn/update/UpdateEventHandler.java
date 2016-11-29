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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnFileUrlMapping;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lesya
*/
public class UpdateEventHandler implements ProgressTracker {
  private ProgressIndicator myProgressIndicator;
  private UpdatedFiles myUpdatedFiles;
  private int myExternalsCount;
  private final SvnVcs myVCS;
  @Nullable private final SvnUpdateContext mySequentialUpdatesContext;
  private final Map<File, SVNURL> myUrlToCheckForSwitch;
  // pair.first - group id, pair.second - file path
  // Stack is used to correctly handle cases when updates of externals occur during ordinary update, because these inner updates could have
  // its own revisions.
  private final Stack<List<Pair<String, String>>> myFilesWaitingForRevision;

  protected String myText;
  protected String myText2;

  public UpdateEventHandler(SvnVcs vcs, ProgressIndicator progressIndicator,
                            @Nullable final SvnUpdateContext sequentialUpdatesContext) {
    myProgressIndicator = progressIndicator;
    myVCS = vcs;
    mySequentialUpdatesContext = sequentialUpdatesContext;
    myExternalsCount = 1;
    myUrlToCheckForSwitch = new HashMap<>();
    myFilesWaitingForRevision = ContainerUtil.newStack();
  }

  /**
   * Same UpdateEventHandler instance could be used to update several roots - for instance, when updating whole project that contains
   * multiple working copies => so this method explicitly indicates when update of new root is started (to correctly collect updated
   * files).
   * <p/>
   * Still UPDATE_NONE (which is currently fired by command line and by SVNKit for 1.6 and below working copies - and is just skipped by
   * UpdateEventHandler) or UPDATE_STARTED (which is currently fired by SVNKit for 1.7 working copies) events should be considered for
   * such purposes, especially if further we want to support commands like "svn update <folder1> <folder2>".
   * <p/>
   * TODO: Check if UPDATE_NONE is fired in some other cases by SVNKit.
   * <p/>
   * TODO: Currently for command line UPDATE_NONE event could be fired several times for the same folder - as "svn update" output is
   * TODO: processed line by line, "Updating '.'" line (which results in firing UPDATE_NONE) is printed before auth request and then
   * TODO: the command could be repeated with new credentials. This case should also be handled if we want to rely on UPDATE_NONE or
   * TODO: UPDATE_STARTED event in some code paths.
   */
  public void startUpdate() {
    myFilesWaitingForRevision.push(ContainerUtil.<Pair<String, String>>newArrayList());
  }

  public void finishUpdate() {
    while (!myFilesWaitingForRevision.isEmpty()) {
      setRevisionForWaitingFiles(CommitInfo.EMPTY.getRevision());
    }
  }

  public void addToSwitch(final File file, final SVNURL url) {
    myUrlToCheckForSwitch.put(file, url);
  }

  public void setUpdatedFiles(final UpdatedFiles updatedFiles) {
    myUpdatedFiles = updatedFiles;
  }

  public void consume(final ProgressEvent event) {
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

    if (event.getAction() == EventAction.TREE_CONFLICT) {
      myText2 = SvnBundle.message("progress.text2.treeconflicted", displayPath);
      updateProgressIndicator();
      myUpdatedFiles.registerGroup(createFileGroup(VcsBundle.message("update.group.name.merged.with.tree.conflicts"),
                                                   FileGroup.MERGED_WITH_TREE_CONFLICT));
      addFileToGroup(FileGroup.MERGED_WITH_TREE_CONFLICT, event);
    }

    if (event.getAction() == EventAction.UPDATE_ADD ||
        event.getAction() == EventAction.ADD) {
      myText2 = SvnBundle.message("progress.text2.added", displayPath);
      if (event.getContentsStatus() == StatusType.CONFLICTED || event.getPropertiesStatus() == StatusType.CONFLICTED) {
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
    else if (event.getAction() == EventAction.UPDATE_NONE) {
      // skip it
      return;
    }
    else if (event.getAction() == EventAction.UPDATE_DELETE) {
      myText2 = SvnBundle.message("progress.text2.deleted", displayPath);
      addFileToGroup(FileGroup.REMOVED_FROM_REPOSITORY_ID, event);
    }
    else if (event.getAction() == EventAction.UPDATE_UPDATE) {
      possiblySwitched(event);
      if (event.getContentsStatus() == StatusType.CONFLICTED || event.getPropertiesStatus() == StatusType.CONFLICTED) {
        if (event.getContentsStatus() == StatusType.CONFLICTED) {
          addFileToGroup(FileGroup.MERGED_WITH_CONFLICT_ID, event);
        }
        if (event.getPropertiesStatus() == StatusType.CONFLICTED) {
          addFileToGroup(FileGroup.MERGED_WITH_PROPERTY_CONFLICT_ID, event);
        }
        myText2 = SvnBundle.message("progress.text2.conflicted", displayPath);
      }
      else if (event.getContentsStatus() == StatusType.MERGED || event.getPropertiesStatus() == StatusType.MERGED) {
        myText2 = SvnBundle.message("progres.text2.merged", displayPath);
        addFileToGroup(FileGroup.MERGED_ID, event);
      }
      else if (event.getContentsStatus() == StatusType.CHANGED || event.getPropertiesStatus() == StatusType.CHANGED) {
        myText2 = SvnBundle.message("progres.text2.updated", displayPath);
        addFileToGroup(FileGroup.UPDATED_ID, event);
      }
      else if (event.getContentsStatus() == StatusType.UNCHANGED &&
               (event.getPropertiesStatus() == StatusType.UNCHANGED || event.getPropertiesStatus() == StatusType.UNKNOWN)) {
        myText2 = SvnBundle.message("progres.text2.updated", displayPath);
      } else if (StatusType.INAPPLICABLE.equals(event.getContentsStatus()) &&
                 (event.getPropertiesStatus() == StatusType.UNCHANGED || event.getPropertiesStatus() == StatusType.UNKNOWN)) {
        myText2 = SvnBundle.message("progres.text2.updated", displayPath);
      }
      else {
        myText2 = "";
        addFileToGroup(FileGroup.UNKNOWN_ID, event);
      }
    }
    else if (event.getAction() == EventAction.UPDATE_EXTERNAL) {
      if (mySequentialUpdatesContext != null) {
        mySequentialUpdatesContext.registerExternalRootBeingUpdated(event.getFile());
      }
      myFilesWaitingForRevision.push(ContainerUtil.<Pair<String, String>>newArrayList());
      myExternalsCount++;
      myText = SvnBundle.message("progress.text.updating.external.location", event.getFile().getAbsolutePath());
    }
    else if (event.getAction() == EventAction.RESTORE) {
      myText2 = SvnBundle.message("progress.text2.restored.file", displayPath);
      addFileToGroup(FileGroup.RESTORED_ID, event);
    }
    else if (event.getAction() == EventAction.UPDATE_COMPLETED && event.getRevision() >= 0) {
      possiblySwitched(event);
      setRevisionForWaitingFiles(event.getRevision());
      myExternalsCount--;
      myText2 = SvnBundle.message("progres.text2.updated.to.revision", event.getRevision());
      if (myExternalsCount == 0) {
        myExternalsCount = 1;
        StatusBar.Info.set(SvnBundle.message("status.text.updated.to.revision", event.getRevision()), myVCS.getProject());
      }
    }
    else if (event.getAction() == EventAction.SKIP) {
      myText2 = SvnBundle.message("progress.text2.skipped.file", displayPath);
      addFileToGroup(FileGroup.SKIPPED_ID, event);
    }

    updateProgressIndicator();
  }

  private void possiblySwitched(ProgressEvent event) {
    final File file = event.getFile();
    if (file == null) return;
    final SVNURL wasUrl = myUrlToCheckForSwitch.get(file);
    if (wasUrl != null && ! wasUrl.equals(event.getURL())) {
      myUrlToCheckForSwitch.remove(file);
      addFileToGroup(FileGroup.SWITCHED_ID, event);
    }
  }

  private boolean itemSwitched(final ProgressEvent event) {
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

  protected boolean handleInDescendants(final ProgressEvent event) {
    return false;
  }

  protected void addFileToGroup(final String id, final ProgressEvent event) {
    final FileGroup fileGroup = myUpdatedFiles.getGroupById(id);
    final String path = event.getFile().getAbsolutePath();
    myFilesWaitingForRevision.peek().add(Pair.create(id, path));
    if (event.getErrorMessage() != null) {
      fileGroup.addError(path, event.getErrorMessage().getMessage());
    }
  }

  private void setRevisionForWaitingFiles(long revisionNumber) {
    SvnRevisionNumber revision = new SvnRevisionNumber(SVNRevision.create(revisionNumber));

    for (Pair<String, String> pair : myFilesWaitingForRevision.pop()) {
      FileGroup fileGroup = myUpdatedFiles.getGroupById(pair.getFirst());

      fileGroup.add(pair.getSecond(), SvnVcs.getKey(), revision);
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
