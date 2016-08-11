/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchDifferentiatedDialog;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchExecutor;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchMode;
import com.intellij.openapi.vcs.changes.patch.TextFilePatchInProgress;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.continuation.Continuation;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.IOException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/18/12
 * Time: 2:44 PM
 */
public class MergeFromTheirsResolver {
  private final SvnVcs myVcs;
  private final TreeConflictDescription myDescription;
  private final Change myChange;
  private final FilePath myOldFilePath;
  private final FilePath myNewFilePath;
  private final String myOldPresentation;
  private final String myNewPresentation;
  private final SvnRevisionNumber myCommittedRevision;
  private Boolean myAdd;

  private final List<Change> myTheirsChanges;
  private final List<Change> myTheirsBinaryChanges;
  private final List<VcsException> myWarnings;
  private List<TextFilePatch> myTextPatches;
  private VirtualFile myBaseForPatch;

  public MergeFromTheirsResolver(SvnVcs vcs, TreeConflictDescription description, Change change, SvnRevisionNumber revision) {
    myVcs = vcs;
    myDescription = description;
    myChange = change;
    myCommittedRevision = revision;
    myOldFilePath = myChange.getBeforeRevision().getFile();
    myNewFilePath = myChange.getAfterRevision().getFile();
    myBaseForPatch = ChangesUtil.findValidParentAccurately(myNewFilePath);
    myOldPresentation = TreeConflictRefreshablePanel.filePath(myOldFilePath);
    myNewPresentation = TreeConflictRefreshablePanel.filePath(myNewFilePath);

    myTheirsChanges = new ArrayList<>();
    myTheirsBinaryChanges = new ArrayList<>();
    myWarnings = new ArrayList<>();
    myTextPatches = Collections.emptyList();
  }

  public void execute() {
    int ok = Messages.showOkCancelDialog(myVcs.getProject(), (myChange.isMoved() ?
      SvnBundle.message("confirmation.resolve.tree.conflict.merge.moved", myOldPresentation, myNewPresentation) :
      SvnBundle.message("confirmation.resolve.tree.conflict.merge.renamed", myOldPresentation, myNewPresentation)),
                                         TreeConflictRefreshablePanel.TITLE, Messages.getQuestionIcon());
    if (Messages.OK != ok) return;

    FileDocumentManager.getInstance().saveAllDocuments();
    //final String name = "Merge changes from theirs for: " + myOldPresentation;

    final Continuation fragmented = Continuation.createFragmented(myVcs.getProject(), false);
    fragmented.addExceptionHandler(VcsException.class, new Consumer<VcsException>() {
      @Override
      public void consume(VcsException e) {
        myWarnings.add(e);
        if (e.isWarning()) {
          return;
        }
        AbstractVcsHelper.getInstance(myVcs.getProject()).showErrors(myWarnings, TreeConflictRefreshablePanel.TITLE);
      }
    });

    final List<TaskDescriptor> tasks = new SmartList<>();
    tasks.add(myDescription.isDirectory() ? new PreloadChangesContentsForDir() : new PreloadChangesContentsForFile());
    tasks.add(new ConvertTextPaths());
    tasks.add(new PatchCreator());
    tasks.add(new SelectPatchesInApplyPatchDialog());
    tasks.add(new SelectBinaryFiles());

    fragmented.run(tasks);
  }

  private void appendResolveConflictToContext(final ContinuationContext context) {
    context.next(new ResolveConflictInSvn());
  }

  private void appendTailToContextLast(final ContinuationContext context) {
    context.last(new ApplyBinaryChanges(), new FinalNotification());
  }

  private List<Change> filterOutBinary(List<Change> paths) {
    List<Change> result = null;
    for (Iterator<Change> iterator = paths.iterator(); iterator.hasNext(); ) {
      final Change change = iterator.next();
      if (ChangesUtil.isBinaryChange(change)) {
        result = (result == null ? new SmartList<>() : result);
        result.add(change);
        iterator.remove();
      }
    }
    return result;
  }

  private class FinalNotification extends TaskDescriptor {
    private FinalNotification() {
      super("", Where.AWT);
    }

    @Override
    public void run(ContinuationContext context) {
      final StringBuilder message = new StringBuilder().append("Theirs changes merged for ").append(myOldPresentation);
      VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), message.toString(), MessageType.INFO);
      if (! myWarnings.isEmpty()) {
        AbstractVcsHelper.getInstance(myVcs.getProject()).showErrors(myWarnings, TreeConflictRefreshablePanel.TITLE);
      }
    }
  }

  private class ResolveConflictInSvn extends TaskDescriptor {
    private ResolveConflictInSvn() {
      super("Accepting working state", Where.POOLED);
    }

    @Override
    public void run(ContinuationContext context) {
      try {
        new SvnTreeConflictResolver(myVcs, myOldFilePath, null).resolveSelectMineFull();
      }
      catch (VcsException e1) {
        context.handleException(e1, false);
      }
    }
  }

  private class ConvertTextPaths extends TaskDescriptor {
    private ConvertTextPaths() {
      super("", Where.AWT);
    }

    @Override
    public void run(ContinuationContext context) {
      initAddOption();
      List<Change> convertedChanges = new SmartList<>();
      try {
        // revision contents is preloaded, so ok to call in awt
        convertedChanges = convertPaths(myTheirsChanges);
      }
      catch (VcsException e) {
        context.handleException(e, true);
      }
      myTheirsChanges.clear();
      myTheirsChanges.addAll(convertedChanges);
    }
  }

  private class SelectPatchesInApplyPatchDialog extends TaskDescriptor {
    private SelectPatchesInApplyPatchDialog() {
      super("", Where.AWT);
    }

    @Override
    public void run(ContinuationContext context) {
      final ChangeListManager clManager = ChangeListManager.getInstance(myVcs.getProject());
      final LocalChangeList changeList = clManager.getChangeList(myChange);
      final ApplyPatchDifferentiatedDialog dialog = new ApplyPatchDifferentiatedDialog(myVcs.getProject(),
        new TreeConflictApplyTheirsPatchExecutor(myVcs, context, myBaseForPatch),
        Collections.<ApplyPatchExecutor>singletonList(new ApplyPatchSaveToFileExecutor(myVcs.getProject(), myBaseForPatch)),
        ApplyPatchMode.APPLY_PATCH_IN_MEMORY, myTextPatches, changeList);
      context.suspend();
      dialog.show();
    }
  }

  private class TreeConflictApplyTheirsPatchExecutor implements ApplyPatchExecutor<TextFilePatchInProgress> {
    private final SvnVcs myVcs;
    private final ContinuationContext myInner;
    private final VirtualFile myBaseDir;

    public TreeConflictApplyTheirsPatchExecutor(SvnVcs vcs, ContinuationContext inner, final VirtualFile baseDir) {
      myVcs = vcs;
      myInner = inner;
      myBaseDir = baseDir;
    }

    @Override
    public String getName() {
      return "Apply Patch";
    }

    @Override
    public void apply(@NotNull List<FilePatch> remaining, @NotNull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroupsToApply,
                      @Nullable LocalChangeList localList,
                      @Nullable String fileName,
                      @Nullable TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
      final List<FilePatch> patches;
      try {
        patches = ApplyPatchSaveToFileExecutor.patchGroupsToOneGroup(patchGroupsToApply, myBaseDir);
      }
      catch (IOException e) {
        myInner.handleException(e, true);
        return;
      }

      final PatchApplier<BinaryFilePatch> patchApplier =
        new PatchApplier<>(myVcs.getProject(), myBaseDir, patches, localList, null, null);
      patchApplier.execute(false, true);  // 3
      boolean thereAreCreations = false;
      for (FilePatch patch : patches) {
        if (patch.isNewFile() || ! Comparing.equal(patch.getAfterName(), patch.getBeforeName())) {
          thereAreCreations = true;
          break;
        }
      }
      if (thereAreCreations) {
        // restore deletion of old directory:
        myInner.next(new DirectoryAddition());  // 2
      }
      appendResolveConflictToContext(myInner);  // 1
      appendTailToContextLast(myInner); // 4
      myInner.ping();
    }
  }

  private class DirectoryAddition extends TaskDescriptor {
    private DirectoryAddition() {
      super("Adding " + myOldPresentation + " to Subversion", Where.POOLED);
    }

    @Override
    public void run(ContinuationContext context) {
      try {
        // TODO: Previously SVNKit client was invoked with mkDir=true option - so corresponding directory would be created. Now mkDir=false
        // TODO: is used. Command line also does not support automatic directory creation.
        // TODO: Need to check additionally if there are cases when directory does not exist and add corresponding code.
        myVcs.getFactory(myOldFilePath.getIOFile()).createAddClient()
          .add(myOldFilePath.getIOFile(), Depth.EMPTY, true, false, true, null);
      }
      catch (VcsException e) {
        context.handleException(e, true);
      }
    }
  }

  private class PatchCreator extends TaskDescriptor {
    private PatchCreator() {
      super("Creating patch for theirs changes", Where.POOLED);
    }

    @Override
    public void run(ContinuationContext context) {
      final Project project = myVcs.getProject();
      final List<FilePatch> patches;
      try {
        patches = IdeaTextPatchBuilder.buildPatch(project, myTheirsChanges, ObjectUtils.assertNotNull(myBaseForPatch).getPath(), false);
        myTextPatches = ObjectsConvertor.convert(patches, new Convertor<FilePatch, TextFilePatch>() {
          @Override
          public TextFilePatch convert(FilePatch o) {
            return (TextFilePatch)o;
          }
        });
      }
      catch (VcsException e) {
        context.handleException(e, true);
      }
    }
  }

  private class SelectBinaryFiles extends TaskDescriptor {
    private SelectBinaryFiles() {
      super("", Where.AWT);
    }

    @Override
    public void run(ContinuationContext context) {
      if (myTheirsBinaryChanges.isEmpty()) return;
      final List<Change> converted;
      try {
        converted = convertPaths(myTheirsBinaryChanges);
      }
      catch (VcsException e) {
        context.handleException(e, true);
        return;
      }
      if (converted.isEmpty()) return;
      final Map<FilePath, Change> map = new HashMap<>();
      for (Change change : converted) {
        map.put(ChangesUtil.getFilePath(change), change);
      }
      final Collection<FilePath> selected = chooseBinaryFiles(converted, map.keySet());
      myTheirsBinaryChanges.clear();
      for (FilePath filePath : selected) {
        myTheirsBinaryChanges.add(map.get(filePath));
      }
    }
  }

  private class ApplyBinaryChanges extends TaskDescriptor {
    private ApplyBinaryChanges() {
      super("", Where.AWT);
    }

    @Override
    public void run(final ContinuationContext context) {
      if (myTheirsBinaryChanges.isEmpty()) return;
      final Application application = ApplicationManager.getApplication();
      final List<FilePath> dirtyPaths = new ArrayList<>();
      for (final Change change : myTheirsBinaryChanges) {
        try {
          application.runWriteAction(new ThrowableComputable<Void, VcsException>() {
            @Override
            public Void compute() throws VcsException {
              try {
                if (change.getAfterRevision() == null) {
                  final FilePath path = change.getBeforeRevision().getFile();
                  dirtyPaths.add(path);
                  final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.getIOFile());
                  if (file == null) {
                    context.handleException(new VcsException("Can not delete file: " + file.getPath(), true), false);
                    return null;
                  }
                  file.delete(TreeConflictRefreshablePanel.class);
                }
                else {
                  final FilePath file = change.getAfterRevision().getFile();
                  dirtyPaths.add(file);
                  final String parentPath = file.getParentPath().getPath();
                  final VirtualFile parentFile = VfsUtil.createDirectoryIfMissing(parentPath);
                  if (parentFile == null) {
                    context.handleException(new VcsException("Can not create directory: " + parentPath, true), false);
                    return null;
                  }
                  final VirtualFile child = parentFile.createChildData(TreeConflictRefreshablePanel.class, file.getName());
                  if (child == null) {
                    context.handleException(new VcsException("Can not create file: " + file.getPath(), true), false);
                    return null;
                  }
                  final BinaryContentRevision revision = (BinaryContentRevision)change.getAfterRevision();
                  final byte[] content = revision.getBinaryContent();
                  // actually it was the fix for IDEA-91572 Error saving merged data: Argument 0 for @NotNull parameter of > com/intellij/
                  if (content == null) {
                    context.handleException(new VcsException("Can not load Theirs content for file " + file.getPath()), false);
                    return null;
                  }
                  child.setBinaryContent(content);
                }
              }
              catch (IOException e) {
                throw new VcsException(e);
              }
              return null;
            }
          });
        }
        catch (VcsException e) {
          context.handleException(e, true);
          return;
        }
      }
      VcsDirtyScopeManager.getInstance(myVcs.getProject()).filePathsDirty(dirtyPaths, null);
    }
  }

  private Collection<FilePath> chooseBinaryFiles(List<Change> converted, Set<FilePath> paths) {
    String singleMessage = "";
    if (paths.size() == 1) {
      final Change change = converted.get(0);
      final FileStatus status = change.getFileStatus();
      final FilePath path = ChangesUtil.getFilePath(change);
      final String stringPath = TreeConflictRefreshablePanel.filePath(path);
      if (FileStatus.DELETED.equals(status)) {
        singleMessage = "Delete binary file " + stringPath + " (according to theirs changes)?";
      } else if (FileStatus.ADDED.equals(status)) {
        singleMessage = "Create binary file " + stringPath + " (according to theirs changes)?";
      } else {
        singleMessage = "Apply changes to binary file " + stringPath + " (according to theirs changes)?";
      }
    }
    return AbstractVcsHelper.getInstance(myVcs.getProject()).selectFilePathsToProcess(new ArrayList<>(paths),
      TreeConflictRefreshablePanel.TITLE, "Select binary files to patch", TreeConflictRefreshablePanel.TITLE,
      singleMessage, new VcsShowConfirmationOption() {

      @Override
      public Value getValue() {
        return null;
      }

      @Override
      public void setValue(Value value) {
      }

      @Override
      public boolean isPersistent() {
        return false;
      }
    });
  }

  private List<Change> convertPaths(List<Change> changesForPatch) throws VcsException {
    initAddOption();
    final List<Change> changes = new ArrayList<>();
    for (Change change : changesForPatch) {
      if (! isUnderOldDir(change, myOldFilePath)) continue;
      ContentRevision before = null;
      ContentRevision after = null;
      if (change.getBeforeRevision() != null) {
        before = new SimpleContentRevision(change.getBeforeRevision().getContent(),
          rebasePath(myOldFilePath, myNewFilePath, change.getBeforeRevision().getFile()),
          change.getBeforeRevision().getRevisionNumber().asString());
      }
      if (change.getAfterRevision() != null) {
        // if addition or move - do not move to the new path
        if (myAdd && (change.getBeforeRevision() == null || change.isMoved() || change.isRenamed())) {
          after = change.getAfterRevision();
        } else {
          after = new SimpleContentRevision(change.getAfterRevision().getContent(),
                                            rebasePath(myOldFilePath, myNewFilePath, change.getAfterRevision().getFile()),
                                            change.getAfterRevision().getRevisionNumber().asString());
        }
      }
      changes.add(new Change(before, after));
    }
    return changes;
  }

  private boolean isUnderOldDir(Change change, FilePath path) {
    if (change.getBeforeRevision() != null) {
      final boolean isUnder = FileUtil.isAncestor(path.getIOFile(), change.getBeforeRevision().getFile().getIOFile(), true);
      if (isUnder) {
        return true;
      }
    }
    if (change.getAfterRevision() != null) {
      final boolean isUnder = FileUtil.isAncestor(path.getIOFile(), change.getAfterRevision().getFile().getIOFile(), true);
      if (isUnder) {
        return isUnder;
      }
    }
    return false;
  }

  @NotNull
  private static FilePath rebasePath(@NotNull FilePath oldBase, @NotNull FilePath newBase, @NotNull FilePath path) {
    String relativePath = ObjectUtils.assertNotNull(FileUtil.getRelativePath(oldBase.getPath(), path.getPath(), '/'));
    return VcsUtil.getFilePath(newBase.getPath() + "/" + relativePath, path.isDirectory());
  }

  private class PreloadChangesContentsForFile extends TaskDescriptor {
    private PreloadChangesContentsForFile() {
      super("Getting base and theirs revisions content", Where.POOLED);
    }

    @Override
    public void run(ContinuationContext context) {
      final SvnContentRevision base = SvnContentRevision.createBaseRevision(myVcs, myNewFilePath, myCommittedRevision.getRevision());
      final SvnContentRevision remote = SvnContentRevision.createRemote(myVcs, myOldFilePath, SVNRevision.create(
                                                                          myDescription.getSourceRightVersion().getPegRevision()));
      try {
        final ContentRevision newBase = new SimpleContentRevision(base.getContent(), myNewFilePath, base.getRevisionNumber().asString());
        final ContentRevision newRemote = new SimpleContentRevision(remote.getContent(), myNewFilePath, remote.getRevisionNumber().asString());
        myTheirsChanges.add(new Change(newBase, newRemote));
      }
      catch (VcsException e) {
        context.handleException(e, true);
      }
    }
  }

  private class PreloadChangesContentsForDir extends TaskDescriptor {
    private PreloadChangesContentsForDir() {
      super("Getting base and theirs revisions content", Where.POOLED);
    }

    @Override
    public void run(ContinuationContext context) {
      final List<Change> changesForPatch;
      try {
        final List<CommittedChangeList> lst = loadSvnChangeListsForPatch(myDescription);
        changesForPatch = CommittedChangesTreeBrowser.collectChanges(lst, true);
        for (Change change : changesForPatch) {
          if (change.getBeforeRevision() != null) {
            preloadRevisionContents(change.getBeforeRevision());
          }
          if (change.getAfterRevision() != null) {
            preloadRevisionContents(change.getAfterRevision());
          }
        }
      }
      catch (VcsException e) {
        context.handleException(e, true);
        return;
      }
      final List<Change> binaryChanges = filterOutBinary(changesForPatch);
      if (binaryChanges != null && ! binaryChanges.isEmpty()) {
        myTheirsBinaryChanges.addAll(binaryChanges);
      }
      if (! changesForPatch.isEmpty()) {
        myTheirsChanges.addAll(changesForPatch);
      }
    }
  }

  private void preloadRevisionContents(ContentRevision cr) throws VcsException {
    if (cr instanceof BinaryContentRevision) {
      ((BinaryContentRevision) cr).getBinaryContent();
    } else {
      cr.getContent();
    }
  }

  private List<CommittedChangeList> loadSvnChangeListsForPatch(TreeConflictDescription description) throws VcsException {
    long max = description.getSourceRightVersion().getPegRevision();
    long min = description.getSourceLeftVersion().getPegRevision();

    final ChangeBrowserSettings settings = new ChangeBrowserSettings();
    settings.USE_CHANGE_BEFORE_FILTER = settings.USE_CHANGE_AFTER_FILTER = true;
    settings.CHANGE_BEFORE = "" + max;
    settings.CHANGE_AFTER = "" + min;
    final List<SvnChangeList> committedChanges = myVcs.getCachingCommittedChangesProvider().getCommittedChanges(
      settings, new SvnRepositoryLocation(description.getSourceRightVersion().getRepositoryRoot().toString()), 0);
    final List<CommittedChangeList> lst = new ArrayList<>(committedChanges.size() - 1);
    for (SvnChangeList change : committedChanges) {
      if (change.getNumber() == min) {
        continue;
      }
      lst.add(change);
    }
    return lst;
  }

  private void initAddOption() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myAdd == null) {
      myAdd = getAddedFilesPlaceOption();
    }
  }

  private boolean getAddedFilesPlaceOption() {
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
    boolean add = Boolean.TRUE.equals(configuration.isKeepNewFilesAsIsForTreeConflictMerge());
    if (configuration.isKeepNewFilesAsIsForTreeConflictMerge() != null) {
      return add;
    }
    if (!containAdditions(myTheirsChanges) && !containAdditions(myTheirsBinaryChanges)) {
      return false;
    }
    return Messages.YES == MessageDialogBuilder.yesNo(TreeConflictRefreshablePanel.TITLE, "Keep newly created file(s) in their original place?").yesText("Keep").noText("Move").doNotAsk(
      new DialogWrapper.DoNotAskOption() {
        @Override
        public boolean isToBeShown() {
          return true;
        }

        @Override
        public void setToBeShown(boolean value, int exitCode) {
          if (!value) {
            if (exitCode == 0) {
              // yes
              configuration.setKeepNewFilesAsIsForTreeConflictMerge(true);
            }
            else {
              configuration.setKeepNewFilesAsIsForTreeConflictMerge(false);
            }
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
          return CommonBundle.message("dialog.options.do.not.ask");
        }
      }).show();
  }

  private boolean containAdditions(final List<Change> changes) {
    boolean addFound = false;
    for (Change change : changes) {
      if (change.getBeforeRevision() == null || change.isMoved() || change.isRenamed()) {
        addFound = true;
        break;
      }
    }
    return addFound;
  }
}
