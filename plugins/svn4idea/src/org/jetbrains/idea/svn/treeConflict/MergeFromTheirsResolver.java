// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDifferentiatedDialog;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchExecutor;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchMode;
import com.intellij.openapi.vcs.changes.patch.TextFilePatchInProgress;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static com.intellij.openapi.ui.MessageDialogBuilder.yesNo;
import static com.intellij.openapi.ui.Messages.showOkCancelDialog;
import static com.intellij.openapi.util.io.FileUtil.getRelativePath;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.vcs.changes.ChangesUtil.*;
import static com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView;
import static com.intellij.util.ExceptionUtil.rethrowAllAsUnchecked;
import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;
import static org.jetbrains.idea.svn.SvnBundle.message;

public final class MergeFromTheirsResolver extends BackgroundTaskGroup {
  @NotNull private final SvnVcs myVcs;
  @NotNull private final TreeConflictDescription myDescription;
  @NotNull private final Change myChange;
  @NotNull private final FilePath myOldFilePath;
  @NotNull private final FilePath myNewFilePath;
  @NotNull private final String myOldPresentation;
  @NotNull private final String myNewPresentation;
  private final SvnRevisionNumber myCommittedRevision;
  private Boolean myAdd;

  @NotNull private final List<Change> myTheirsChanges;
  @NotNull private final List<Change> myTheirsBinaryChanges;
  private List<FilePatch> myTextPatches;
  private final VirtualFile myBaseForPatch;
  private boolean myThereAreCreations;

  public MergeFromTheirsResolver(@NotNull SvnVcs vcs,
                                 @NotNull TreeConflictDescription description,
                                 @NotNull Change change,
                                 SvnRevisionNumber revision) {
    super(vcs.getProject(), message("progress.title.resolve.tree.conflict"));
    myVcs = vcs;
    myDescription = description;
    myChange = change;
    myCommittedRevision = revision;
    myOldFilePath = Objects.requireNonNull(myChange.getBeforeRevision()).getFile();
    myNewFilePath = Objects.requireNonNull(myChange.getAfterRevision()).getFile();
    myBaseForPatch = findValidParentAccurately(myNewFilePath);
    myOldPresentation = TreeConflictRefreshablePanel.filePath(myOldFilePath);
    myNewPresentation = TreeConflictRefreshablePanel.filePath(myNewFilePath);

    myTheirsChanges = new ArrayList<>();
    myTheirsBinaryChanges = new ArrayList<>();
    myTextPatches = emptyList();
  }

  @RequiresEdt
  public void execute() {
    String message = myChange.isMoved()
                     ? message("confirmation.resolve.tree.conflict.merge.moved", myOldPresentation, myNewPresentation)
                     : message("confirmation.resolve.tree.conflict.merge.renamed", myOldPresentation, myNewPresentation);
    int ok = showOkCancelDialog(myVcs.getProject(), message, message("dialog.title.resolve.tree.conflict"), Messages.getQuestionIcon());
    if (Messages.OK != ok) return;

    FileDocumentManager.getInstance().saveAllDocuments();

    runInBackground(message("progress.title.getting.base.and.theirs.revisions.content"), indicator -> preloadContent());
    runInEdt(this::convertTextPaths);
    runInBackground(message("progress.title.creating.patch.for.theirs.changes"), indicator -> createPatches());
    runInEdt(() -> selectPatchesInApplyPatchDialog(exception -> {
      if (exception == null) {
        runInBackground(message("progress.title.accepting.working.state"), indicator -> resolveConflicts());
        if (myThereAreCreations) {
          runInBackground(message("progress.title.adding.file.to.subversion", myOldPresentation), indicator -> addDirectories());
        }
        runInEdt(this::selectBinaryFiles);
        runInBackground(message("progress.title.applying.binary.changes"), indicator -> applyBinaryChanges());
        runInEdt(this::notifyMergeIsFinished);
      }
      else {
        addError(exception);
        showErrors();
      }
    }));
  }

  private void notifyMergeIsFinished() {
    showOverChangesView(myVcs.getProject(), message("message.theirs.changes.merged.for.file", myOldPresentation), MessageType.INFO);
    showErrors();
  }

  private void resolveConflicts() throws VcsException {
    new SvnTreeConflictResolver(myVcs, myOldFilePath, null).resolveSelectMineFull();
  }

  @RequiresEdt
  private void convertTextPaths() throws VcsException {
    // revision contents is preloaded, so ok to call in awt
    List<Change> convertedChanges = convertPaths(myTheirsChanges);
    myTheirsChanges.clear();
    myTheirsChanges.addAll(convertedChanges);
  }

  @RequiresEdt
  private void selectPatchesInApplyPatchDialog(@NotNull Consumer<VcsException> callback) {
    LocalChangeList changeList = ChangeListManager.getInstance(myVcs.getProject()).getChangeList(myChange);
    TreeConflictApplyTheirsPatchExecutor patchExecutor = new TreeConflictApplyTheirsPatchExecutor(myVcs, myBaseForPatch);
    ApplyPatchDifferentiatedDialog dialog = new ApplyPatchDifferentiatedDialog(
      myVcs.getProject(), patchExecutor, singletonList(new ApplyPatchSaveToFileExecutor(myVcs.getProject(), myBaseForPatch)),
      ApplyPatchMode.APPLY_PATCH_IN_MEMORY, myTextPatches, changeList);

    // dialog is not modal - so such async behavior is used
    patchExecutor.myPromise.onSuccess(callback);
    dialog.show();
  }

  private class TreeConflictApplyTheirsPatchExecutor implements ApplyPatchExecutor<TextFilePatchInProgress> {
    @NotNull private final SvnVcs myVcs;
    private final VirtualFile myBaseDir;
    @NotNull private final AsyncPromise<VcsException> myPromise;

    TreeConflictApplyTheirsPatchExecutor(@NotNull SvnVcs vcs, final VirtualFile baseDir) {
      myVcs = vcs;
      myBaseDir = baseDir;
      myPromise = new AsyncPromise<>();
    }

    @Override
    public String getName() {
      return VcsBundle.message("button.apply.patch");
    }

    @Override
    public void apply(@NotNull List<? extends FilePatch> remaining,
                      @NotNull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroupsToApply,
                      @Nullable LocalChangeList localList,
                      @Nullable String fileName,
                      @Nullable ThrowableComputable<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
      new Task.Backgroundable(myVcs.getProject(), VcsBundle.message("patch.apply.progress.title")) {
        VcsException myException = null;

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            List<FilePatch> patches = ApplyPatchSaveToFileExecutor.toOnePatchGroup(patchGroupsToApply, myBaseDir);
            new PatchApplier(Objects.requireNonNull(myProject), myBaseDir, patches, localList, null).execute(false, true);
            myThereAreCreations =
              patches.stream().anyMatch(patch -> patch.isNewFile() || !Objects.equals(patch.getAfterName(), patch.getBeforeName()));
          }
          catch (IOException e) {
            myException = new VcsException(e);
          }
        }

        @Override
        public void onFinished() {
          myPromise.setResult(myException);
        }
      }.queue();
    }
  }

  private void addDirectories() throws VcsException {
    // TODO: Previously SVNKit client was invoked with mkDir=true option - so corresponding directory would be created. Now mkDir=false
    // TODO: is used. Command line also does not support automatic directory creation.
    // TODO: Need to check additionally if there are cases when directory does not exist and add corresponding code.
    myVcs.getFactory(myOldFilePath.getIOFile()).createAddClient().add(myOldFilePath.getIOFile(), Depth.EMPTY, true, false, true, null);
  }

  private void createPatches() throws VcsException {
    List<FilePatch> patches = IdeaTextPatchBuilder.buildPatch(myVcs.getProject(), myTheirsChanges, Objects.requireNonNull(myBaseForPatch).toNioPath(), false);
    myTextPatches = map(patches, TextFilePatch.class::cast);
  }

  @RequiresEdt
  private void selectBinaryFiles() throws VcsException {
    List<Change> converted = convertPaths(myTheirsBinaryChanges);
    if (!converted.isEmpty()) {
      Map<FilePath, Change> map = converted.stream().collect(toMap(ChangesUtil::getFilePath, identity()));
      Collection<FilePath> selected = chooseBinaryFiles(converted, map.keySet());

      myTheirsBinaryChanges.clear();
      if (!ContainerUtil.isEmpty(selected)) {
        for (FilePath filePath : selected) {
          myTheirsBinaryChanges.add(map.get(filePath));
        }
      }
    }
  }

  private void applyBinaryChanges() throws VcsException {
    List<FilePath> dirtyPaths = new ArrayList<>();
    for (Change change : myTheirsBinaryChanges) {
      try {
        WriteAction.runAndWait(() -> {
          dirtyPaths.add(getFilePath(change));
          try {
            applyBinaryChange(change);
          }
          catch (IOException e) {
            throw new VcsException(e);
          }
        });
      }
      catch (Throwable e) {
        processBinaryChangeError(e);
      }
    }
    VcsDirtyScopeManager.getInstance(myVcs.getProject()).filePathsDirty(dirtyPaths, null);
  }

  private void processBinaryChangeError(@NotNull Throwable error) throws VcsException {
    if (error instanceof VcsException) {
      VcsException vcsError = (VcsException)error;
      if (vcsError.isWarning()) {
        addError(vcsError);
      }
      else {
        throw vcsError;
      }
    }
    else {
      rethrowAllAsUnchecked(error);
    }
  }

  private static void applyBinaryChange(@NotNull Change change) throws IOException, VcsException {
    if (change.getAfterRevision() == null) {
      FilePath path = Objects.requireNonNull(change.getBeforeRevision()).getFile();
      VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.getPath());
      if (file == null) {
        throw new VcsException(message("error.can.not.delete.file", path.getPath()), true);
      }
      file.delete(TreeConflictRefreshablePanel.class);
    }
    else {
      FilePath file = change.getAfterRevision().getFile();
      String parentPath = Objects.requireNonNull(file.getParentPath()).getPath();
      VirtualFile parentFile = VfsUtil.createDirectoryIfMissing(parentPath);
      if (parentFile == null) {
        throw new VcsException(message("error.can.not.create.directory", parentPath), true);
      }
      VirtualFile child = parentFile.createChildData(TreeConflictRefreshablePanel.class, file.getName());
      byte[] content = ((BinaryContentRevision)change.getAfterRevision()).getBinaryContent();
      // actually it was the fix for IDEA-91572 Error saving merged data: Argument 0 for @NotNull parameter of > com/intellij/
      if (content == null) {
        throw new VcsException(message("error.can.not.load.theirs.content.for.file", file.getPath()), true);
      }
      child.setBinaryContent(content);
    }
  }

  @Nullable
  private Collection<FilePath> chooseBinaryFiles(@NotNull List<Change> changes, @NotNull Set<FilePath> paths) {
    return AbstractVcsHelper.getInstance(myVcs.getProject()).selectFilePathsToProcess(
      new ArrayList<>(paths),
      message("dialog.title.resolve.tree.conflict"), message("dialog.message.select.binary.files.to.patch"),
      message("dialog.title.resolve.tree.conflict"), changes.size() == 1 ? getSingleBinaryFileMessage(changes.get(0)) : "",
      VcsShowConfirmationOption.STATIC_SHOW_CONFIRMATION
    );
  }

  private static @DialogMessage @NotNull String getSingleBinaryFileMessage(@NotNull Change change) {
    String path = TreeConflictRefreshablePanel.filePath(getFilePath(change));

    return FileStatus.DELETED.equals(change.getFileStatus())
           ? message("dialog.message.merge.from.theirs.delete.binary.file", path)
           : FileStatus.ADDED.equals(change.getFileStatus())
             ? message("dialog.message.merge.from.theirs.create.binary.file", path)
             : message("dialog.message.merge.from.theirs.modify.binary.file", path);
  }

  @RequiresEdt
  @NotNull
  private List<Change> convertPaths(@NotNull List<Change> changes) throws VcsException {
    initAddOption();

    List<Change> result = new ArrayList<>();
    for (Change change : changes) {
      if (isUnderOldDir(change, myOldFilePath)) {
        result
          .add(new Change(convertBeforeRevision(change.getBeforeRevision()), convertAfterRevision(change, change.getAfterRevision())));
      }
    }
    return result;
  }

  @Nullable
  private ContentRevision convertBeforeRevision(@Nullable ContentRevision revision) throws VcsException {
    return revision != null ? toSimpleRevision(revision, true) : null;
  }

  @Nullable
  private ContentRevision convertAfterRevision(@NotNull Change change, @Nullable ContentRevision revision) throws VcsException {
    if (revision == null) return null;
    return myAdd && (change.getBeforeRevision() == null || change.isMoved() || change.isRenamed()) ? revision : toSimpleRevision(revision,
                                                                                                                                 true);
  }

  @NotNull
  private SimpleContentRevision toSimpleRevision(@NotNull ContentRevision revision, boolean rebasePath) throws VcsException {
    return new SimpleContentRevision(revision.getContent(),
                                     rebasePath ? rebasePath(myOldFilePath, myNewFilePath, revision.getFile()) : myNewFilePath,
                                     revision.getRevisionNumber().asString());
  }

  private static boolean isUnderOldDir(@NotNull Change change, @NotNull FilePath path) {
    FilePath beforePath = getBeforePath(change);
    FilePath afterPath = getAfterPath(change);

    return beforePath != null && isAncestor(path.getPath(), beforePath.getPath(), true) ||
           afterPath != null && isAncestor(path.getPath(), afterPath.getPath(), true);
  }

  @NotNull
  private static FilePath rebasePath(@NotNull FilePath oldBase, @NotNull FilePath newBase, @NotNull FilePath path) {
    String relativePath = Objects.requireNonNull(getRelativePath(oldBase.getPath(), path.getPath(), '/'));
    return VcsUtil.getFilePath(newBase.getPath() + "/" + relativePath, path.isDirectory());
  }

  private void preloadContent() throws VcsException {
    if (myDescription.isDirectory()) {
      preloadForDirectory();
    }
    else {
      preloadForFile();
    }
  }

  private void preloadForFile() throws VcsException {
    SvnContentRevision base = SvnContentRevision.createBaseRevision(myVcs, myNewFilePath, myCommittedRevision.getRevision());
    SvnContentRevision remote =
      SvnContentRevision.createRemote(myVcs, myOldFilePath, Revision.of(myDescription.getSourceRightVersion().getPegRevision()));
    myTheirsChanges.add(new Change(toSimpleRevision(base, false), toSimpleRevision(remote, false)));
  }

  private void preloadForDirectory() throws VcsException {
    List<Change> changes = CommittedChangesTreeBrowser.collectChanges(loadSvnChangeListsForPatch(myDescription), true);
    for (Change change : changes) {
      preloadRevisionContents(change.getBeforeRevision());
      preloadRevisionContents(change.getAfterRevision());
    }
    Map<Boolean, List<Change>> changesSplit = changes.stream().collect(partitioningBy(MergeFromTheirsResolver::isBinaryChange));
    myTheirsBinaryChanges.addAll(changesSplit.get(Boolean.TRUE));
    myTheirsChanges.addAll(changesSplit.get(Boolean.FALSE));
  }

  private static void preloadRevisionContents(@Nullable ContentRevision revision) throws VcsException {
    if (revision != null) {
      if (revision instanceof BinaryContentRevision) {
        ((BinaryContentRevision)revision).getBinaryContent();
      }
      else {
        revision.getContent();
      }
    }
  }

  private @NotNull List<SvnChangeList> loadSvnChangeListsForPatch(@NotNull TreeConflictDescription description) throws VcsException {
    long max = description.getSourceRightVersion().getPegRevision();
    long min = description.getSourceLeftVersion().getPegRevision();
    SvnRepositoryLocation location = new SvnRepositoryLocation(description.getSourceRightVersion().getRepositoryRoot());
    ChangeBrowserSettings settings = new ChangeBrowserSettings();
    settings.USE_CHANGE_BEFORE_FILTER = settings.USE_CHANGE_AFTER_FILTER = true;
    settings.CHANGE_BEFORE = String.valueOf(max);
    settings.CHANGE_AFTER = String.valueOf(min);

    @SuppressWarnings("rawtypes")
    CachingCommittedChangesProvider provider = Objects.requireNonNull(myVcs.getCachingCommittedChangesProvider());
    //noinspection unchecked
    List<SvnChangeList> committedChanges = provider.getCommittedChanges(settings, location, 0);
    return filter(committedChanges, changeList -> changeList.getNumber() != min);
  }

  @RequiresEdt
  private void initAddOption() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myAdd == null) {
      myAdd = getAddedFilesPlaceOption();
    }
  }

  private boolean getAddedFilesPlaceOption() {
    SvnConfiguration configuration = myVcs.getSvnConfiguration();
    Boolean add = configuration.isKeepNewFilesAsIsForTreeConflictMerge();

    if (add != null) {
      return add;
    }
    if (!containAdditions(myTheirsChanges) && !containAdditions(myTheirsBinaryChanges)) {
      return false;
    }
    return Messages.YES ==
           yesNo(message("dialog.title.resolve.tree.conflict"), message("dialog.message.keep.newly.created.files.in.their.original.place"))
             .yesText(message("button.keep"))
             .noText(message("button.move"))
             .doNotAsk(
               new DialogWrapper.DoNotAskOption() {
                 @Override
                 public boolean isToBeShown() {
                   return true;
                 }

                 @Override
                 public void setToBeShown(boolean value, int exitCode) {
                   if (!value) {
                     configuration.setKeepNewFilesAsIsForTreeConflictMerge(exitCode == 0);
                   }
                 }

                 @Override
                 public boolean canBeHidden() {
                   return true;
                 }

                 @Override
                 public boolean shouldSaveOptionsOnCancel() {
                   return true;
                 }

                 @NotNull
                 @Override
                 public String getDoNotShowMessage() {
                   return UIBundle.message("dialog.options.do.not.ask");
                 }
               })
             .show();
  }

  private static boolean containAdditions(@NotNull List<Change> changes) {
    return changes.stream().anyMatch(change -> change.getBeforeRevision() == null || change.isMoved() || change.isRenamed());
  }

  private static boolean isBinaryContentRevision(@Nullable ContentRevision revision) {
    return revision instanceof BinaryContentRevision && !revision.getFile().isDirectory();
  }

  private static boolean isBinaryChange(@NotNull Change change) {
    return isBinaryContentRevision(change.getBeforeRevision()) || isBinaryContentRevision(change.getAfterRevision());
  }
}
