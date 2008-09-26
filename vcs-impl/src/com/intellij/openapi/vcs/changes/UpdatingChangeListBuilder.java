package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

class UpdatingChangeListBuilder implements ChangelistBuilder {
  private final ChangeListWorker myChangeListWorker;
  private final FileHolderComposite myComposite;
  // todo +-
  private final Getter<Boolean> myDisposedGetter;
  private VcsDirtyScope myScope;
  private FoldersCutDownWorker myFoldersCutDownWorker;
  private final boolean myUpdateUnversioned;
  private final IgnoredFilesComponent myIgnoredFilesComponent;
  private final ExcludedFileIndex myIndex;

  UpdatingChangeListBuilder(final ChangeListWorker changeListWorker,
                            final FileHolderComposite composite,
                            final Getter<Boolean> disposedGetter,
                            final boolean updateUnversioned,
                            final IgnoredFilesComponent ignoredFilesComponent) {
    myChangeListWorker = changeListWorker;
    myComposite = composite;
    myDisposedGetter = disposedGetter;
    myUpdateUnversioned = updateUnversioned;
    myIgnoredFilesComponent = ignoredFilesComponent;
    myIndex = ExcludedFileIndex.getInstance(changeListWorker.getProject());
  }

  private void checkIfDisposed() {
    if (myDisposedGetter.get()) throw new ChangeListManagerImpl.DisposedException();
  }

  public void setCurrent(final VcsDirtyScope scope, final FoldersCutDownWorker foldersWorker) {
    myScope = scope;
    myFoldersCutDownWorker = foldersWorker;
  }

  public void processChange(final Change change) {
    processChangeInList( change, (ChangeList) null );
  }

  public void processChangeInList(final Change change, @Nullable final ChangeList changeList) {
    checkIfDisposed();

    final String fileName = ChangesUtil.getFilePath(change).getName();
    if (FileTypeManager.getInstance().isFileIgnored(fileName)) return;

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (ChangeListManagerImpl.isUnder(change, myScope)) {
          if (changeList != null) {
            myChangeListWorker.addChangeToList(changeList.getName(), change);
          } else {
            myChangeListWorker.addChangeToCorrespondingList(change);
          }
        }
      }
    });
  }

  public void processChangeInList(final Change change, final String changeListName) {
    checkIfDisposed();

    LocalChangeList list = null;
    if (changeListName != null) {
      list = myChangeListWorker.getCopyByName(changeListName);
      if (list == null) {
        list = myChangeListWorker.addChangeList(changeListName, null, true);
        myChangeListWorker.startProcessingChanges(list.getName(), myScope);
      }
    }
    processChangeInList(change, list);
  }

  public void processUnversionedFile(final VirtualFile file) {
    if (file == null || ! myUpdateUnversioned) return;
    checkIfDisposed();
    if (myIndex.isExcludedFile(file)) return;
    if (myScope.belongsTo(new FilePathImpl(file))) {
      if (myIgnoredFilesComponent.isIgnoredFile(file)) {
        myComposite.getVFHolder(FileHolder.HolderType.IGNORED).addFile(file);
      }
      else {
        myComposite.getVFHolder(FileHolder.HolderType.UNVERSIONED).addFile(file);
      }
      // if a file was previously marked as switched through recursion, remove it from switched list
      myComposite.getSwitchedFileHolder().removeFile(file);
    }
  }

  public void processLocallyDeletedFile(final FilePath file) {
    if (! myUpdateUnversioned) return;
    checkIfDisposed();
    if (FileTypeManager.getInstance().isFileIgnored(file.getName())) return;
    if (myScope.belongsTo(file)) {
      myComposite.getDeletedFilesHolder().addFile(file);
    }
  }

  public void processModifiedWithoutCheckout(final VirtualFile file) {
    if (file == null || ! myUpdateUnversioned) return;
    checkIfDisposed();
    if (myIndex.isExcludedFile(file)) return;
    if (myScope.belongsTo(new FilePathImpl(file))) {
      myComposite.getVFHolder(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING).addFile(file);
    }
  }

  public void processIgnoredFile(final VirtualFile file) {
    if (file == null || ! myUpdateUnversioned) return;
    checkIfDisposed();
    if (myIndex.isExcludedFile(file)) return;
    if (myScope.belongsTo(new FilePathImpl(file))) {
      myComposite.getVFHolder(FileHolder.HolderType.IGNORED).addFile(file);
    }
  }

  public void processLockedFolder(final VirtualFile file) {
    if (file == null) return;
    checkIfDisposed();
    if (myScope.belongsTo(new FilePathImpl(file))) {
      if (myFoldersCutDownWorker.addCurrent(file)) {
        myComposite.getVFHolder(FileHolder.HolderType.LOCKED).addFile(file);
      }
    }
  }

  public void processSwitchedFile(final VirtualFile file, final String branch, final boolean recursive) {
    if (file == null || ! myUpdateUnversioned) return;
    checkIfDisposed();
    if (myIndex.isExcludedFile(file)) return;
    if (myScope.belongsTo(new FilePathImpl(file))) {
      myComposite.getSwitchedFileHolder().addFile(file, branch, recursive);
    }
  }

  public boolean isUpdatingUnversionedFiles() {
    return myUpdateUnversioned;
  }
}
