package com.intellij.openapi.diff.impl.patch.formove;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.ApplyPatchStatus;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.patch.RelativePathCalculator;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * for patches. for shelve.
 */
public class PatchApplier {
  private final Project myProject;
  private final VirtualFile myBaseDirectory;
  private final List<FilePatch> myPatches;
  private final CustomBinaryPatchApplier myCustomForBinaries;
  private final LocalChangeList myTargetChangeList;

  private final List<FilePatch> myRemainingPatches;

  public PatchApplier(final Project project, final VirtualFile baseDirectory, final List<FilePatch> patches,
                      final LocalChangeList targetChangeList, final CustomBinaryPatchApplier customForBinaries) {
    myProject = project;
    myBaseDirectory = baseDirectory;
    myPatches = patches;
    myTargetChangeList = targetChangeList;
    myCustomForBinaries = customForBinaries;
    myRemainingPatches = new ArrayList<FilePatch>();
  }

  public ApplyPatchStatus execute() {
    final PathsVerifier verifier = new PathsVerifier(myProject, myBaseDirectory, myPatches);

    myRemainingPatches.addAll(myPatches);

    final Ref<ApplyPatchStatus> refStatus = new Ref<ApplyPatchStatus>(ApplyPatchStatus.FAILURE);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            if (! verifier.execute()) {
              return;
            }

            if (! makeWritable(verifier.getWritableFiles())) {
              return;
            }

            final List<Pair<VirtualFile, FilePatch>> textPatches = verifier.getTextPatches();
            if (! fileTypesAreOk(textPatches)) {
              return;
            }

            final ApplyPatchStatus status = actualApply(verifier);
            showApplyStatus(status);

            if (status != null) {
              refStatus.set(status);
            }
          } // end of Command run
        }, VcsBundle.message("patch.apply.command"), null);
      }
    });

    final List<VirtualFile> directlyAffected = verifier.getDirectlyAffected();
    final List<VirtualFile> indirectlyAffected = verifier.getAllAffected();

    if ((myTargetChangeList != null) && (! directlyAffected.isEmpty())) {
      ApplyPatchAction.moveChangesOfVsToList(myProject, directlyAffected, myTargetChangeList);
    } else {
      final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      changeListManager.scheduleUpdate();
    }
    refreshIndirectlyAffected(indirectlyAffected);

    return refStatus.get();
  }

  @Nullable
  private ApplyPatchStatus actualApply(final PathsVerifier verifier) {
    final List<Pair<VirtualFile, FilePatch>> textPatches = verifier.getTextPatches();
    final ApplyPatchContext context = new ApplyPatchContext(myBaseDirectory, 0, true, true);
    ApplyPatchStatus status = null;

    status = applyList(textPatches, context, status);

    if (myCustomForBinaries == null) {
      status = applyList(verifier.getBinaryPatches(), context, status);
    } else {
      try {
        final ApplyPatchStatus patchStatus = myCustomForBinaries.apply(verifier.getBinaryPatches());
        status = ApplyPatchStatus.and(status, patchStatus);
        myRemainingPatches.removeAll(myCustomForBinaries.getAppliedPatches());
      }
      catch (IOException e) {
        showError(myProject, e.getMessage(), true);
        return ApplyPatchStatus.FAILURE;
      }
    }
    return status;
  }

  private ApplyPatchStatus applyList(final List<Pair<VirtualFile, FilePatch>> textPatches, final ApplyPatchContext context,
                                     ApplyPatchStatus status) {
    for (Pair<VirtualFile, FilePatch> textPatch : textPatches) {
      final ApplyPatchStatus patchStatus =
          ApplyPatchAction.applyOnly(myProject, textPatch.getSecond(), context, textPatch.getFirst());
      if (ApplyPatchStatus.SUCCESS.equals(patchStatus)) {
        myRemainingPatches.remove(textPatch.getSecond());
      }
      status = ApplyPatchStatus.and(status, patchStatus);
    }
    return status;
  }

  private void showApplyStatus(final ApplyPatchStatus status) {
    if (status == ApplyPatchStatus.ALREADY_APPLIED) {
      showError(myProject, VcsBundle.message("patch.apply.already.applied"), false);
    }
    else if (status == ApplyPatchStatus.PARTIAL) {
      showError(myProject, VcsBundle.message("patch.apply.partially.applied"), false);
    } else if (ApplyPatchStatus.SUCCESS.equals(status)) {
      showError(myProject, VcsBundle.message("patch.apply.success.applied.text"), false);
    }
  }

  public List<FilePatch> getRemainingPatches() {
    return myRemainingPatches;
  }

  private boolean makeWritable(final List<VirtualFile> filesToMakeWritable) {
    final VirtualFile[] fileArray = filesToMakeWritable.toArray(new VirtualFile[filesToMakeWritable.size()]);
    final ReadonlyStatusHandler.OperationStatus readonlyStatus = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(fileArray);
    return (! readonlyStatus.hasReadonlyFiles());
  }

  private boolean fileTypesAreOk(final List<Pair<VirtualFile, FilePatch>> textPatches) {
    for (Pair<VirtualFile, FilePatch> textPatch : textPatches) {
      final VirtualFile file = textPatch.getFirst();
      if (! file.isDirectory()) {
        FileType fileType = file.getFileType();
        if (fileType == FileTypes.UNKNOWN) {
          fileType = FileTypeChooser.associateFileType(file.getPresentableName());
          if (fileType == null) {
            showError(myProject, "Cannot apply patch. File " + file.getPresentableName() + " type not defined.", true);
            return false;
          }
        }
      }
    }
    return true;
  }

  private void refreshIndirectlyAffected(final List<VirtualFile> files) {
    Collections.sort(files, FilePathComparator.getInstance());

    for (VirtualFile file : files) {
      file.refresh(true, false);
    }
}

  public static void showError(final Project project, final String message, final boolean error) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }
    final String title = VcsBundle.message("patch.apply.dialog.title");
    final Runnable messageShower = new Runnable() {
      public void run() {
        if (error) {
          Messages.showErrorDialog(project, message, title);
        }
        else {
          Messages.showInfoMessage(project, message, title);
        }
      }
    };
    if (application.isDispatchThread()) {
      messageShower.run();
    } else {
      application.invokeLater(new Runnable() {
        public void run() {
          messageShower.run();
        }
      });
    }
  }

  @Nullable
  public static VirtualFile getFile(final VirtualFile baseDir, final String path) {
    if (path == null) {
      return null;
    }
    final List<String> tail = new ArrayList<String>();
    final VirtualFile file = getFile(baseDir, path, tail);
    if (tail.isEmpty()) {
      return file;
    }
    return null;
  }

  @Nullable
  public static VirtualFile getFile(final VirtualFile baseDir, final String path, final List<String> tail) {
    VirtualFile child = baseDir;

    final String[] pieces = RelativePathCalculator.split(path);

    for (int i = 0; i < pieces.length; i++) {
      final String piece = pieces[i];
      if (child == null) {
        return null;
      }
      if ("".equals(piece) || ".".equals(piece)) {
        continue;
      }
      if ("..".equals(piece)) {
        child = child.getParent();
        continue;
      }

      VirtualFile nextChild = child.findChild(piece);
      if (nextChild == null) {
        if (tail != null) {
          for (int j = i; j < pieces.length; j++) {
            final String pieceInner = pieces[j];
            tail.add(pieceInner);
          }
        }
        return child;
      }
      child = nextChild;
    }

    return child;
  }

}
