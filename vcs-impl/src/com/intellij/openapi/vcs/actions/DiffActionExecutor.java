package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.diff.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;

public abstract class DiffActionExecutor {
  protected final DiffProvider myDiffProvider;
  protected final VirtualFile mySelectedFile;
  protected final Project myProject;

  protected DiffActionExecutor(final DiffProvider diffProvider, final VirtualFile selectedFile, final Project project) {
    myDiffProvider = diffProvider;
    mySelectedFile = selectedFile;
    myProject = project;
  }

  @Nullable
  protected DiffContent createRemote(final VcsRevisionNumber revisionNumber) throws IOException, VcsException {
    final ContentRevision fileRevision = myDiffProvider.createFileContent(revisionNumber, mySelectedFile);
    if (fileRevision != null) {
      final Ref<VcsException> ex = new Ref<VcsException>();
      final StringBuilder contentBuilder = new StringBuilder();
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            final String content = fileRevision.getContent();
            if (!(fileRevision instanceof BinaryContentRevision)) {
              if (content == null) {
                ex.set(new VcsException("Failed to load content"));
              }
              contentBuilder.append(content);
            }
          }
          catch (VcsException e) {
            ex.set(e);
          }
        }
      }, VcsBundle.message("show.diff.progress.title"), true, myProject);
      if (!ex.isNull()) {
        AbstractVcsHelper.getInstance(myProject).showError(ex.get(), VcsBundle.message("message.title.diff"));
        return null;
      }

      if (fileRevision instanceof BinaryContentRevision) {
        if (Arrays.equals(mySelectedFile.contentsToByteArray(), ((BinaryContentRevision) fileRevision).getBinaryContent())) {
          Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.are.identical"), VcsBundle.message("message.title.diff"));
        } else {
          Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.are.different"), VcsBundle.message("message.title.diff"));
        }
        return null;
      }

      return new SimpleContent(contentBuilder.toString(), mySelectedFile.getFileType());
    }
    return null;
  }

  public void showDiff() {
    final VcsRevisionNumber revisionNumber = getRevisionNumber();
    try {
      if (revisionNumber == null) {
        return;
      }
      final DiffContent remote = createRemote(revisionNumber);

      final SimpleDiffRequest request = new SimpleDiffRequest(myProject, mySelectedFile.getPresentableUrl());
      final DocumentContent content2 = new DocumentContent(myProject, FileDocumentManager.getInstance().getDocument(mySelectedFile));

      final VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(mySelectedFile);

      if (revisionNumber.compareTo(currentRevision) > 0) {
        request.setContents(content2, remote);
        request.setContentTitles(VcsBundle.message("diff.title.local"), revisionNumber.asString());
      } else {
        request.setContents(remote, content2);
        request.setContentTitles(revisionNumber.asString(), VcsBundle.message("diff.title.local"));
      }

      request.addHint(DiffTool.HINT_SHOW_FRAME);
      DiffManager.getInstance().getDiffTool().show(request);
    }
    catch (ProcessCanceledException e) {
      //ignore
    }
    catch (VcsException e) {
      AbstractVcsHelper.getInstance(myProject).showError(e, VcsBundle.message("message.title.diff"));
    }
    catch (IOException e) {
      AbstractVcsHelper.getInstance(myProject).showError(new VcsException(e), VcsBundle.message("message.title.diff"));
    }
  }

  public static void showDiff(final DiffProvider diffProvider, final VcsRevisionNumber revisionNumber, final VirtualFile selectedFile,
                        final Project project) {
    final DiffActionExecutor executor = new CompareToFixedExecutor(diffProvider, selectedFile, project, revisionNumber);
    executor.showDiff();
  }

  @Nullable
  protected abstract VcsRevisionNumber getRevisionNumber();

  public static class CompareToFixedExecutor extends DiffActionExecutor {
    private final VcsRevisionNumber myNumber;

    public CompareToFixedExecutor(final DiffProvider diffProvider,
                                  final VirtualFile selectedFile, final Project project, final VcsRevisionNumber number) {
      super(diffProvider, selectedFile, project);
      myNumber = number;
    }

    protected VcsRevisionNumber getRevisionNumber() {
      return myNumber;
    }
  }

  public static class CompareToCurrentExecutor extends DiffActionExecutor {
    public CompareToCurrentExecutor(final DiffProvider diffProvider, final VirtualFile selectedFile, final Project project) {
      super(diffProvider, selectedFile, project);
    }

    @Nullable
    protected VcsRevisionNumber getRevisionNumber() {
      return myDiffProvider.getCurrentRevision(mySelectedFile);
    }
  }

  public static class DeletionAwareExecutor extends DiffActionExecutor {
    private boolean myFileStillExists;

    public DeletionAwareExecutor(final DiffProvider diffProvider,
                                 final VirtualFile selectedFile, final Project project) {
      super(diffProvider, selectedFile, project);
    }

    protected VcsRevisionNumber getRevisionNumber() {
      final ItemLatestState itemState = myDiffProvider.getLastRevision(mySelectedFile);
      if (itemState == null) {
        return null;
      }
      myFileStillExists = itemState.isItemExists();
      return itemState.getNumber();
    }

    @Override
    protected DiffContent createRemote(final VcsRevisionNumber revisionNumber) throws IOException, VcsException {
      if (myFileStillExists) {
        return super.createRemote(revisionNumber);
      } else {
        return new SimpleContent("", mySelectedFile.getFileType());
      }
    }
  }
}
