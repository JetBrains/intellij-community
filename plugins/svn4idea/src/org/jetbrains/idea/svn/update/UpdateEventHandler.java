// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.progress.ProcessCanceledException;
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
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.status.StatusType;

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
  private final Map<File, Url> myUrlToCheckForSwitch;
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
    myFilesWaitingForRevision.push(ContainerUtil.newArrayList());
  }

  public void finishUpdate() {
    while (!myFilesWaitingForRevision.isEmpty()) {
      setRevisionForWaitingFiles(CommitInfo.EMPTY.getRevision());
    }
  }

  public void addToSwitch(final File file, final Url url) {
    myUrlToCheckForSwitch.put(file, url);
  }

  public void setUpdatedFiles(final UpdatedFiles updatedFiles) {
    myUpdatedFiles = updatedFiles;
  }

  @Override
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
      myFilesWaitingForRevision.push(ContainerUtil.newArrayList());
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
    final Url wasUrl = myUrlToCheckForSwitch.get(file);
    if (wasUrl != null && ! wasUrl.equals(event.getURL())) {
      myUrlToCheckForSwitch.remove(file);
      addFileToGroup(FileGroup.SWITCHED_ID, event);
    }
  }

  private boolean itemSwitched(final ProgressEvent event) {
    final File file = event.getFile();
    final SvnFileUrlMapping urlMapping = myVCS.getSvnFileUrlMapping();
    final Url currentUrl = urlMapping.getUrlForFile(file);
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
      fileGroup.addError(path, event.getErrorMessage());
    }
  }

  private void setRevisionForWaitingFiles(long revisionNumber) {
    SvnRevisionNumber revision = new SvnRevisionNumber(Revision.of(revisionNumber));

    for (Pair<String, String> pair : myFilesWaitingForRevision.pop()) {
      FileGroup fileGroup = myUpdatedFiles.getGroupById(pair.getFirst());

      fileGroup.add(pair.getSecond(), SvnVcs.getKey(), revision);
    }
  }

  public void checkCancelled() throws ProcessCanceledException {
    if (myProgressIndicator != null) {
      myProgressIndicator.checkCanceled();
    }
  }

  private static FileGroup createFileGroup(String name, String id) {
    return new FileGroup(name, name, false, id, true);
  }

  public void setProgressIndicator(ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
  }
}
