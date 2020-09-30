// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.SvnMergeProvider;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractSvnUpdateIntegrateEnvironment implements UpdateEnvironment {
  protected final SvnVcs myVcs;
  private final ProjectLevelVcsManager myVcsManager;
  @NonNls public static final String REPLACED_ID = "replaced";

  protected AbstractSvnUpdateIntegrateEnvironment(final SvnVcs vcs) {
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(vcs.getProject());
  }

  @Override
  public void fillGroups(UpdatedFiles updatedFiles) {
    updatedFiles.registerGroup(new FileGroup(VcsBundle.message("update.group.name.merged.with.property.conflicts"),
                                       VcsBundle.message("status.group.name.will.be.merged.with.property.conflicts"), false,
                                       FileGroup.MERGED_WITH_PROPERTY_CONFLICT_ID, false));
    updatedFiles.registerGroup(new FileGroup(VcsBundle.message("update.group.name.merged.with.tree.conflicts"),
                                       VcsBundle.message("status.group.name.will.be.merged.with.tree.conflicts"), false,
                                       FileGroup.MERGED_WITH_TREE_CONFLICT, false));
  }

  @Override
  @NotNull
  public UpdateSession updateDirectories(final FilePath @NotNull [] contentRoots,
                                         final UpdatedFiles updatedFiles,
                                         final ProgressIndicator progressIndicator, @NotNull final Ref<SequentialUpdatesContext> context)
    throws ProcessCanceledException {

    if (context.isNull()) {
      context.set(new SvnUpdateContext(myVcs, contentRoots));
    }

    final ArrayList<VcsException> exceptions = new ArrayList<>();
    UpdateEventHandler eventHandler = new UpdateEventHandler(myVcs, progressIndicator, (SvnUpdateContext) context.get());
    eventHandler.setUpdatedFiles(updatedFiles);

    boolean totalUpdate = true;
    AbstractUpdateIntegrateCrawler crawler = createCrawler(eventHandler, totalUpdate, exceptions, updatedFiles);

    Collection<VirtualFile> updatedRoots = new HashSet<>();
    Arrays.sort(contentRoots, (o1, o2) -> SystemInfo.isFileSystemCaseSensitive ? o1.getPath().replace("/", "\\")
      .compareTo(o2.getPath().replace("/", "\\")) : o1.getPath().replace("/", "\\").compareToIgnoreCase(o2.getPath().replace("/", "\\")));
    for (FilePath contentRoot : contentRoots) {
      if (progressIndicator != null) {
        progressIndicator.checkCanceled();
      }
      final File ioRoot = contentRoot.getIOFile();
      if (! ((SvnUpdateContext)context.get()).shouldRunFor(ioRoot)) continue;

      updatedRoots.addAll(SvnUtil.crawlWCRoots(myVcs, ioRoot, crawler, progressIndicator));
    }
    if (updatedRoots.isEmpty()) {
      WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> Messages
        .showErrorDialog(myVcs.getProject(), SvnBundle.message("message.text.update.no.directories.found"),
                         SvnBundle.message("messate.text.update.error")), null, myVcs.getProject());
      return new UpdateSessionAdapter(Collections.emptyList(), true);
    }

    return new MyUpdateSessionAdapter(contentRoots, updatedFiles, exceptions);
  }

  private final class MyUpdateSessionAdapter extends UpdateSessionAdapter {
    private final FilePath[] myContentRoots;
    private final UpdatedFiles myUpdatedFiles;
    private final VcsDirtyScopeManager myDirtyScopeManager;
    private final List<Runnable> myGroupWorkers;

    private MyUpdateSessionAdapter(final FilePath @NotNull [] contentRoots, final UpdatedFiles updatedFiles, final List<VcsException> exceptions) {
      super(exceptions, false);
      myContentRoots = contentRoots;
      myUpdatedFiles = updatedFiles;
      myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myVcs.getProject());

      if (! isDryRun()) {
        myGroupWorkers = Arrays.asList(new MyTextConflictWorker(), new MyConflictWorker(FileGroup.MERGED_WITH_PROPERTY_CONFLICT_ID) {
          @Override
          protected List<VirtualFile> merge() {
            return null;
          }
        }, new MyTreeConflictWorker(), new MyReplacedWorker());
      } else {
        myGroupWorkers = Collections.emptyList();
      }
    }

    // update switched/ignored status of directories
    private void dirtyRoots() {
      final Collection<VirtualFile> vfColl = Arrays.stream(myContentRoots).map(FilePath::getVirtualFile)
        .filter(Objects::nonNull).collect(Collectors.toList());
      myDirtyScopeManager.filesDirty(vfColl, null);
    }

    @Override
    public void onRefreshFilesCompleted() {
      // TODO: why do we need to mark all roots as dirty here???
      dirtyRoots();

      for (Runnable groupWorker : myGroupWorkers) {
        groupWorker.run();
      }
    }

    // not a conflict worker; to correctly show replaced items
    private class MyReplacedWorker implements Runnable {
      @Override
      public void run() {
        final FileGroup replacedGroup = myUpdatedFiles.getGroupById(REPLACED_ID);
        final FileGroup deletedGroup = myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID);
        if ((deletedGroup != null) && (replacedGroup != null) && (! deletedGroup.isEmpty()) && (! replacedGroup.isEmpty())) {
          final Set<String> replacedFiles = new HashSet<>(replacedGroup.getFiles());
          final Collection<String> deletedFiles = new HashSet<>(deletedGroup.getFiles());

          for (String deletedFile : deletedFiles) {
            if (replacedFiles.contains(deletedFile)) {
              deletedGroup.remove(deletedFile);
            }
          }
        }
      }
    }

    // at the moment no resolve, only refresh files & statuses
    private class MyTreeConflictWorker implements Runnable {
      @Override
      public void run() {
        final LocalFileSystem lfs = LocalFileSystem.getInstance();
        final FileGroup conflictedGroup = myUpdatedFiles.getGroupById(FileGroup.MERGED_WITH_TREE_CONFLICT);
        final Collection<String> conflictedFiles = conflictedGroup.getFiles();
        final Collection<VirtualFile> parents = new ArrayList<>();

        if ((conflictedFiles != null) && (! conflictedFiles.isEmpty())) {
          for (final String conflictedFile : conflictedFiles) {
            final File file = new File(conflictedFile);
            final VirtualFile vfFile = lfs.refreshAndFindFileByIoFile(file);
            if (vfFile != null) {
              parents.add(vfFile);
              continue;
            }
            final File parent = file.getParentFile();

            VirtualFile vf = lfs.findFileByIoFile(parent);
            if (vf == null) {
              vf = lfs.refreshAndFindFileByIoFile(parent);
            }
            if (vf != null) {
              parents.add(vf);
            }
          }
        }

        if (! parents.isEmpty()) {
          RefreshQueue.getInstance().refresh(true, true, () -> myDirtyScopeManager.filesDirty(null, parents), parents);
        }
      }
    }

    private final class MyTextConflictWorker extends MyConflictWorker {
      private MyTextConflictWorker() {
        super(FileGroup.MERGED_WITH_CONFLICT_ID);
      }

      @Override
      protected List<VirtualFile> merge() {
        final List<VirtualFile> writable = prepareWritable(myFiles);
        final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(myVcs.getProject());
        return vcsHelper.showMergeDialog(writable, new SvnMergeProvider(myVcs.getProject()));
      }
    }

    private abstract class MyConflictWorker implements Runnable {
      private final String groupId;
      protected final List<VirtualFile> myFiles;
      private final LocalFileSystem myLfs;
      private final ProjectLevelVcsManager myPlVcsManager;

      protected MyConflictWorker(final String groupId) {
        this.groupId = groupId;
        myFiles = new ArrayList<>();
        myLfs = LocalFileSystem.getInstance();
        myPlVcsManager = ProjectLevelVcsManager.getInstance(myVcs.getProject());
      }

      // for reuse
      protected List<VirtualFile> prepareWritable(final Collection<VirtualFile> files) {
        final List<VirtualFile> writable = new ArrayList<>();
        for (VirtualFile vf : files) {
          if (myVcs.equals(myPlVcsManager.getVcsFor(vf))) {
            writable.add(vf);
          }
        }
        final ReadonlyStatusHandler.OperationStatus operationStatus =
          ReadonlyStatusHandler.getInstance(myVcs.getProject()).ensureFilesWritable(writable);
        writable.removeAll(Arrays.asList(operationStatus.getReadonlyFiles()));

        return writable;
      }

      @Nullable
      protected abstract List<VirtualFile> merge();

      @Override
      public void run() {
        fillAndRefreshFiles();
        if (! myFiles.isEmpty()) {
          final List<VirtualFile> merged = merge();
          if (merged != null && (! merged.isEmpty())) {
            moveToMergedGroup(merged);

            // do we need this
            myDirtyScopeManager.filesDirty(merged, null);
          }
        }
      }

      protected void moveToMergedGroup(final List<VirtualFile> merged) {
        final FileGroup conflictedGroup = myUpdatedFiles.getGroupById(groupId);
        FileGroup mergedGroup = myUpdatedFiles.getGroupById(FileGroup.MERGED_ID);
        for (VirtualFile mergedFile: merged) {
          final String path = FileUtil.toSystemDependentName(mergedFile.getPresentableUrl());
          final VcsRevisionNumber revision = conflictedGroup.getRevision(myVcsManager, path);
          conflictedGroup.remove(path);
          mergedGroup.add(path, myVcs.getKeyInstanceMethod(), revision);
        }
      }

      protected void fillAndRefreshFiles() {
        final FileGroup conflictedGroup = myUpdatedFiles.getGroupById(groupId);
        final Collection<String> conflictedFiles = conflictedGroup.getFiles();
        final Collection<VirtualFile> parents = new ArrayList<>();

        if ((conflictedFiles != null) && (! conflictedFiles.isEmpty())) {
          for (final String conflictedFile : conflictedFiles) {
            final File file = new File(conflictedFile);
            VirtualFile vf = myLfs.findFileByIoFile(file);
            if (vf == null) {
              vf = myLfs.refreshAndFindFileByIoFile(file);
            }
            if (vf != null) {
              myFiles.add(vf);
              final VirtualFile parent = vf.getParent();
              if (parent != null) {
                parents.add(parent);
              }
            }
          }
        }

        if (! myFiles.isEmpty()) {
          RefreshQueue.getInstance().refresh(true, true, null, parents);
          myDirtyScopeManager.filesDirty(myFiles, null);
        }
      }
    }
  }

  protected boolean isDryRun() {
    return false;
  }

  protected abstract AbstractUpdateIntegrateCrawler createCrawler(UpdateEventHandler eventHandler,
                                                 boolean totalUpdate,
                                                 ArrayList<VcsException> exceptions, UpdatedFiles updatedFiles);

  @Override
  @Nullable
  public abstract Configurable createConfigurable(Collection<FilePath> collection);
}
