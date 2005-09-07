package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DocumentContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsFileContent;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProcessCanceledException;

import java.io.IOException;
import java.util.Arrays;

public abstract class AbstractShowDiffAction extends AbstractVcsAction{

  protected void update(VcsContext vcsContext, Presentation presentation) {
    updateDiffAction(presentation, vcsContext);
  }

  protected static void updateDiffAction(final Presentation presentation, final VcsContext vcsContext) {
    presentation.setEnabled(isEnabled(vcsContext));
    presentation.setVisible(isVisible(vcsContext));
  }

  protected static boolean isVisible(final VcsContext vcsContext) {
    final Project project = vcsContext.getProject();
    if (project == null) return false;
    final VirtualFile[] selectedFilePaths = vcsContext.getSelectedFiles();
    if (selectedFilePaths == null || selectedFilePaths.length != 1) return false;

    final VirtualFile selectedFile = selectedFilePaths[0];

    if (selectedFile.isDirectory()) return false;

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(selectedFile);
    if (vcs == null) return false;

    final DiffProvider diffProvider = vcs.getDiffProvider();

    if (diffProvider == null) return false;

    return true;

  }

  protected static boolean isEnabled(final VcsContext vcsContext) {
    if (!(isVisible(vcsContext))) return false;

    final Project project = vcsContext.getProject();
    if (project == null) return false;
    final VirtualFile[] selectedFilePaths = vcsContext.getSelectedFiles();
    if (selectedFilePaths == null || selectedFilePaths.length != 1) return false;

    final VirtualFile selectedFile = selectedFilePaths[0];

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(selectedFile);
    if (vcs == null) return false;

    final DiffProvider diffProvider = vcs.getDiffProvider();

    if (diffProvider == null) return false;

    return vcs.fileExistsInVcs(new FilePathImpl(selectedFile)) ;
  }


  protected void actionPerformed(VcsContext vcsContext) {
    final Project project = vcsContext.getProject();
    final VirtualFile selectedFile = vcsContext.getSelectedFiles()[0];

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(selectedFile);
    final DiffProvider diffProvider = vcs.getDiffProvider();

    VcsRevisionNumber revisionNumber = getRevisionNumber(diffProvider, selectedFile);

    showDiff(diffProvider, revisionNumber, selectedFile, project);


  }

  protected static void showDiff(final DiffProvider diffProvider,
                        final VcsRevisionNumber revisionNumber,
                        final VirtualFile selectedFile,
                        final Project project) {
    try {
      final VcsFileContent fileRevision = diffProvider.createFileContent(revisionNumber, selectedFile);
      if (fileRevision != null) {
        fileRevision.loadContent();

        if (selectedFile.getFileType().isBinary()) {
          if (Arrays.equals(selectedFile.contentsToByteArray(), fileRevision.getContent())) {
            Messages.showInfoMessage("Binary versions are identical", "Diff");
          } else {
            Messages.showInfoMessage("Binary versions are different", "Diff");
          }
          return;
        }

        final SimpleDiffRequest request =
        new SimpleDiffRequest(project, selectedFile.getPresentableUrl());
        final SimpleContent content1 = new SimpleContent(new String(fileRevision.getContent(), selectedFile.getCharset().name()), selectedFile.getFileType());
        final DocumentContent content2 = new DocumentContent(project, FileDocumentManager.getInstance().getDocument(selectedFile));

        final VcsRevisionNumber currentRevision = diffProvider.getCurrentRevision(selectedFile);

        if (revisionNumber.compareTo(currentRevision) > 0) {
          request.setContents(content2, content1);
          request.setContentTitles("Local", revisionNumber.asString());
        } else {
          request.setContents(content1, content2);
          request.setContentTitles(revisionNumber.asString(), "Local");
        }

        DiffManager.getInstance().getDiffTool().show(request);
      }
    }
    catch (ProcessCanceledException e) {
      //ignore
    }
    catch (VcsException e) {
      AbstractVcsHelper.getInstance(project).showError(e, "Diff");
    }
    catch (IOException e) {
      AbstractVcsHelper.getInstance(project).showError(new VcsException(e), "Diff");
    }
  }

  protected abstract VcsRevisionNumber getRevisionNumber(DiffProvider diffProvider, VirtualFile file);
}
