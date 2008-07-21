package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PathsVerifier {
  // in
  private final Project myProject;
  private final VirtualFile myBaseDirectory;
  private final List<FilePatch> myPatches;
  // temp
  private final List<MovedFileData> myMovedFiles;
  private final List<VirtualFile> myCreatedDirectories;
  // out
  private final List<Pair<VirtualFile, FilePatch>> myTextPatches;
  private final List<Pair<VirtualFile, FilePatch>> myBinaryPatches;
  private final List<VirtualFile> myWritableFiles;

  public PathsVerifier(final Project project, final VirtualFile baseDirectory, final List<FilePatch> patches) {
    myProject = project;
    myBaseDirectory = baseDirectory;
    myPatches = patches;

    myMovedFiles = new ArrayList<MovedFileData>();
    myCreatedDirectories = new ArrayList<VirtualFile>();
    myTextPatches = new ArrayList<Pair<VirtualFile,FilePatch>>();
    myBinaryPatches = new ArrayList<Pair<VirtualFile,FilePatch>>();
    myWritableFiles = new ArrayList<VirtualFile>();
  }

  // those to be moved to CL: target + created dirs
  public List<VirtualFile> getDirectlyAffected() {
    final List<VirtualFile> affected = new ArrayList<VirtualFile>();
    affected.addAll(myCreatedDirectories);
    affected.addAll(myWritableFiles);
    return affected;
  }

  // old parents of moved files
  public List<VirtualFile> getAllAffected() {
    final List<VirtualFile> affected = getDirectlyAffected();
    for (MovedFileData data : myMovedFiles) {
      affected.add(data.getOldParent());
      affected.add(data.getCurrent().getParent());
    }
    return affected;
  }

  public boolean execute() {
    try {
      final List<CheckPath> checkers = new ArrayList<CheckPath>(myPatches.size());
      for (FilePatch patch : myPatches) {
        final CheckPath checker = getChecker(patch);
        if (! checker.canBeApplied()) {
          revert(checker.getErrorMessage());
          return false;
        }
        checkers.add(checker);
      }
      for (CheckPath checker : checkers) {
        if (! checker.check()) {
          revert(checker.getErrorMessage());
          return false;
        }
      }
      return true;
    }
    catch (IOException e) {
      revert(e.getMessage());
      return false;
    }
  }

  private CheckPath getChecker(final FilePatch patch) {
    final String beforeFileName = patch.getBeforeName();
    final String afterFileName = patch.getAfterName();

    if ((beforeFileName == null) || (patch.isNewFile())) {
      return new CheckAdded(patch);
    } else if ((afterFileName == null) || (patch.isDeletedFile())) {
      return new CheckDeleted(patch);
    } else {
      if (! beforeFileName.equals(afterFileName)) {
        return new CheckMoved(patch);
      } else {
        return new CheckModified(patch);
      }
    }
  }

  private class CheckModified extends CheckDeleted {
    private CheckModified(final FilePatch path) {
      super(path);
    }
  }

  private class CheckDeleted extends CheckPath {
    protected CheckDeleted(final FilePatch path) {
      super(path);
    }

    protected boolean precheck(final VirtualFile beforeFile, final VirtualFile afterFile) {
      if (beforeFile == null) {
        setErrorMessage(fileNotFoundMessage(myBeforeName));
        return false;
      }
      return true;
    }

    protected boolean check() throws IOException {
      final VirtualFile beforeFile = PatchApplier.getFile(myBaseDirectory, myBeforeName);
      // todo maybe deletion may be ok, just warning
      if (! checkExistsAndValid(beforeFile, myBeforeName)) {
        return false;
      }
      addPatch(myPatch, beforeFile);
      return true;
    }
  }

  private class CheckAdded extends CheckPath {
    private CheckAdded(final FilePatch path) {
      super(path);
    }

    protected boolean precheck(final VirtualFile beforeFile, final VirtualFile afterFile) {
      if (afterFile != null) {
        setErrorMessage(fileAlreadyExists(afterFile.getPath()));
        return false;
      }
      return true;
    }

    public boolean check() throws IOException {
      final String[] pieces = RelativePathCalculator.split(myAfterName);
      final VirtualFile parent = makeSureParentPathExists(pieces);
      if (parent == null) {
        setErrorMessage(fileNotFoundMessage(myAfterName));
        return false;
      }
      final VirtualFile file = createFile(parent, pieces[pieces.length - 1]);
      if (! checkExistsAndValid(file, myAfterName)) {
        return false;
      }
      addPatch(myPatch, file);
      return true;
    }
  }

  private class CheckMoved extends CheckPath {
    private CheckMoved(final FilePatch path) {
      super(path);
    }

    // before exists; after does not exist
    protected boolean precheck(final VirtualFile beforeFile, final VirtualFile afterFile) {
      if (beforeFile == null) {
        setErrorMessage(fileNotFoundMessage(myBeforeName));
      } else if (afterFile != null) {
        setErrorMessage(fileAlreadyExists(afterFile.getPath()));
      }
      return (beforeFile != null) && (afterFile == null);
    }

    public boolean check() throws IOException {
      final String[] pieces = RelativePathCalculator.split(myAfterName);
      final VirtualFile afterFileParent = makeSureParentPathExists(pieces);
      if (afterFileParent == null) {
        setErrorMessage(fileNotFoundMessage(myAfterName));
        return false;
      }
      final VirtualFile beforeFile = PatchApplier.getFile(myBaseDirectory, myBeforeName);
      if (! checkExistsAndValid(beforeFile, myBeforeName)) {
        return false;
      }
      final VirtualFile oldParent = beforeFile.getParent();
      final VirtualFile afterFile;
      if (afterFileParent.equals(oldParent)) {
        // rename: no move
        afterFile = beforeFile;
      } else {
        afterFile = moveFile(beforeFile, afterFileParent);
        if (! checkExistsAndValid(afterFile, myAfterName)) {
          return false;
        }
      }
      if (! Comparing.equal(myPatch.getBeforeFileName(), myPatch.getAfterFileName())) {
        afterFile.rename(PatchApplier.class, myPatch.getAfterFileName());
      }
      myMovedFiles.add(new MovedFileData(oldParent, afterFile, myPatch.getBeforeFileName()));
      addPatch(myPatch, afterFile);
      return true;
    }
  }

  private abstract class CheckPath {
    protected final String myBeforeName;
    protected final String myAfterName;
    protected final FilePatch myPatch;
    private String myErrorMessage;

    public CheckPath(final FilePatch path) {
      myPatch = path;
      myBeforeName = path.getBeforeName();
      myAfterName = path.getAfterName();
    }

    public String getErrorMessage() {
      return myErrorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
      myErrorMessage = errorMessage;
    }

    public boolean canBeApplied() {
      final VirtualFile beforeFile = PatchApplier.getFile(myBaseDirectory, myBeforeName);
      final VirtualFile afterFile = PatchApplier.getFile(myBaseDirectory, myAfterName);
      return precheck(beforeFile, afterFile);
    }

    protected abstract boolean precheck(final VirtualFile beforeFile, final VirtualFile afterFile);
    protected abstract boolean check() throws IOException;

    protected boolean checkExistsAndValid(final VirtualFile file, final String name) {
      if (file == null) {
        setErrorMessage(fileNotFoundMessage(name));
        return false;
      }
      return checkModificationValid(file, name);
    }

    protected boolean checkModificationValid(final VirtualFile file, final String name) {
      // security check to avoid overwriting system files with a patch
      if ((file == null) || (! ExcludedFileIndex.getInstance(myProject).isInContent(file)) ||
          (ProjectLevelVcsManager.getInstance(myProject).getVcsRootFor(file) == null)) {
        setErrorMessage("File to patch found outside content root: " + name);
        return false;
      }
      return true;
    }
  }

  private void addPatch(final FilePatch patch, final VirtualFile file) {
    final Pair<VirtualFile, FilePatch> patchPair = new Pair<VirtualFile, FilePatch>(file, patch);
    if (patch instanceof TextFilePatch) {
      myTextPatches.add(patchPair);
    } else {
      myBinaryPatches.add(patchPair);
    }
    myWritableFiles.add(file);
  }

  private String fileNotFoundMessage(final String path) {
    return VcsBundle.message("cannot.find.file.to.patch", path);
  }

  private String fileAlreadyExists(final String path) {
    return VcsBundle.message("cannot.apply.file.already.exists", path);
  }

  private void revert(final String errorMessage) {
    PatchApplier.showError(myProject, errorMessage, true);

    // move back
    /*for (MovedFileData movedFile : myMovedFiles) {
      try {
        final VirtualFile current = movedFile.getCurrent();
        final VirtualFile newParent = current.getParent();
        final VirtualFile file;
        if (! Comparing.equal(newParent, movedFile.getOldParent())) {
          file = moveFile(current, movedFile.getOldParent());
        } else {
          file = current;
        }
        if (! Comparing.equal(current.getName(), movedFile.getOldName())) {
          file.rename(PatchApplier.class, movedFile.getOldName());
        }
      }
      catch (IOException e) {
        // ignore: revert as much as possible
      }
    }

    // go back
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (int i = myCreatedDirectories.size() - 1; i >= 0; -- i) {
          final VirtualFile file = myCreatedDirectories.get(i);
          try {
            file.delete(PatchApplier.class);
          }
          catch (IOException e) {
            // ignore
          }
        }
      }
    });

    myBinaryPatches.clear();
    myTextPatches.clear();
    myWritableFiles.clear();*/
  }


  private VirtualFile createFile(final VirtualFile parent, final String name) throws IOException {
    return parent.createChildData(PatchApplier.class, name);
    /*final Ref<IOException> ioExceptionRef = new Ref<IOException>();
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          result.set(parent.createChildData(PatchApplier.class, name));
        }
        catch (IOException e) {
          ioExceptionRef.set(e);
        }
      }
    });
    if (! ioExceptionRef.isNull()) {
      throw ioExceptionRef.get();
    }
    return result.get();*/
  }

  private VirtualFile moveFile(final VirtualFile file, final VirtualFile newParent) throws IOException {
    file.move(FilePatch.class, newParent);
    return file;
    /*final Ref<IOException> ioExceptionRef = new Ref<IOException>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          file.move(FilePatch.class, newParent);
        }
        catch (IOException e) {
          ioExceptionRef.set(e);
        }
      }
    });
    if (! ioExceptionRef.isNull()) {
      throw ioExceptionRef.get();
    }
    return file;*/
  }

  @Nullable
  private VirtualFile makeSureParentPathExists(final String[] pieces) throws IOException {
    VirtualFile child = myBaseDirectory;

    final int size = (pieces.length - 1);
    for (int i = 0; i < size; i++) {
      final String piece = pieces[i];
      if ("".equals(piece)) {
        continue;
      }
      if ("..".equals(piece)) {
        child = child.getParent();
        continue;
      }

      VirtualFile nextChild = child.findChild(piece);
      if (nextChild == null) {
        nextChild = VfsUtil.createDirectories(child.getPath() + '/' + piece);
        myCreatedDirectories.add(nextChild);
      }
      child = nextChild;
    }
    return child;
  }

  public List<Pair<VirtualFile, FilePatch>> getTextPatches() {
    return myTextPatches;
  }

  public List<Pair<VirtualFile, FilePatch>> getBinaryPatches() {
    return myBinaryPatches;
  }

  public List<VirtualFile> getWritableFiles() {
    return myWritableFiles;
  }

  private static class MovedFileData {
    private final VirtualFile myOldParent;
    private final VirtualFile myCurrent;
    private final String myOldName;

    private MovedFileData(final VirtualFile oldParent, final VirtualFile current, final String oldName) {
      myOldParent = oldParent;
      myCurrent = current;
      myOldName = oldName;
    }

    public VirtualFile getOldParent() {
      return myOldParent;
    }

    public VirtualFile getCurrent() {
      return myCurrent;
    }

    public String getOldName() {
      return myOldName;
    }
  }
}
