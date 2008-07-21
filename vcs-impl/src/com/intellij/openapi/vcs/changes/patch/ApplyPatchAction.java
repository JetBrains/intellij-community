/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2006
 * Time: 17:08:11
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ApplyPatchAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.ApplyPatchAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ApplyPatchDialog dialog = new ApplyPatchDialog(project);
    final VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (vFile != null && vFile.getFileType() == StdFileTypes.PATCH) {
      dialog.setFileName(vFile.getPresentableUrl());
    }
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }

    final List<FilePatch> patches = dialog.getPatches();
    final ApplyPatchContext context = dialog.getApplyPatchContext();

    applySkipDirs(patches, context.getSkipTopDirs());

    new PatchApplier(project, context.getBaseDir(), patches, dialog.getSelectedChangeList(), null).execute();
  }

  public static void applySkipDirs(final List<FilePatch> patches, final int skipDirs) {
    if (skipDirs < 1) {
      return;
    }
    for (FilePatch patch : patches) {
      patch.setBeforeName(skipN(patch.getBeforeName(), skipDirs));
      patch.setAfterName(skipN(patch.getAfterName(), skipDirs));
    }
  }

  private static String skipN(final String path, final int num) {
    final String[] pieces = path.split("/");
    final StringBuilder sb = new StringBuilder();
    for (int i = num; i < pieces.length; i++) {
      final String piece = pieces[i];
      sb.append('/').append(piece);
    }
    return sb.toString();
  }

  public static ApplyPatchStatus applyOnly(final Project project, final FilePatch patch, final ApplyPatchContext context, final VirtualFile file) {
    try {
      return patch.apply(file, context, project);
    }
    catch(ApplyPatchException ex) {
      if (!patch.isNewFile() && !patch.isDeletedFile() && patch instanceof TextFilePatch) {
        ApplyPatchStatus mergeStatus = mergeAgainstBaseVersion(project, file, context, (TextFilePatch) patch,
                                                               ApplyPatchMergeRequestFactory.INSTANCE);
        if (mergeStatus != null) {
          return mergeStatus;
        }
      }
      Messages.showErrorDialog(project, VcsBundle.message("patch.apply.error", patch.getBeforeName(), ex.getMessage()),
                               VcsBundle.message("patch.apply.dialog.title"));
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
    return ApplyPatchStatus.FAILURE;
  }

  @Nullable
  public static ApplyPatchStatus mergeAgainstBaseVersion(Project project, VirtualFile file, ApplyPatchContext context,
                                                         final TextFilePatch patch,
                                                         final PatchMergeRequestFactory mergeRequestFactory) {
    FilePath pathBeforeRename = context.getPathBeforeRename(file);
    final DefaultPatchBaseVersionProvider provider = new DefaultPatchBaseVersionProvider(project, file, patch.getBeforeVersionId());
    if (provider.canProvideContent()) {
      final StringBuilder newText = new StringBuilder();
      final Ref<CharSequence> contentRef = new Ref<CharSequence>();
      final Ref<ApplyPatchStatus> statusRef = new Ref<ApplyPatchStatus>();
      try {
        provider.getBaseVersionContent(pathBeforeRename, new Processor<CharSequence>() {
          public boolean process(final CharSequence text) {
            newText.setLength(0);
            try {
              statusRef.set(patch.applyModifications(text, newText));
            }
            catch(ApplyPatchException ex) {
              return true;  // continue to older versions
            }
            contentRef.set(text);
            return false;
          }
        });
      }
      catch (VcsException vcsEx) {
        Messages.showErrorDialog(project, VcsBundle.message("patch.load.base.revision.error", patch.getBeforeName(), vcsEx.getMessage()),
                                 VcsBundle.message("patch.apply.dialog.title"));
        return ApplyPatchStatus.FAILURE;
      }
      ApplyPatchStatus status = statusRef.get();
      if (status != null) {
        if (status != ApplyPatchStatus.ALREADY_APPLIED) {
          return showMergeDialog(project, file, contentRef.get(), newText.toString(), mergeRequestFactory);
        }
        else {
          return status;
        }
      }
    }
    return null;
  }

  private static ApplyPatchStatus showMergeDialog(Project project, VirtualFile file, CharSequence content, final String patchedContent,
                                                  final PatchMergeRequestFactory mergeRequestFactory) {
    CharSequence fileContent = LoadTextUtil.loadText(file);

    final MergeRequest request = mergeRequestFactory.createMergeRequest(fileContent.toString(), patchedContent, content.toString(), file,
                                                      project);
    DiffManager.getInstance().getDiffTool().show(request);
    if (request.getResult() == DialogWrapper.OK_EXIT_CODE) {
      return ApplyPatchStatus.SUCCESS;
    }
    request.restoreOriginalContent();
    return ApplyPatchStatus.FAILURE;
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (e.getPlace().equals(ActionPlaces.PROJECT_VIEW_POPUP)) {
      VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
      e.getPresentation().setVisible(project != null && vFile != null && vFile.getFileType() == StdFileTypes.PATCH);
    }
    else {
      e.getPresentation().setEnabled(project != null);
    }
  }

  public static void moveChangesOfVsToList(final Project project, final List<VirtualFile> files, final LocalChangeList targetChangeList) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (targetChangeList != changeListManager.getDefaultChangeList()) {
      changeListManager.invokeAfterUpdate(new Runnable() {
        public void run() {
          List<Change> changes = new ArrayList<Change>();
          for(VirtualFile file: files) {
            final Change change = changeListManager.getChange(file);
            if (change != null) {
              changes.add(change);
            }
          }

          changeListManager.moveChangesTo(targetChangeList, changes.toArray(new Change[changes.size()]));
        }
      });
    }
  }

  public static void moveChangesToList(final Project project, final List<FilePath> files, final LocalChangeList targetChangeList) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (targetChangeList != changeListManager.getDefaultChangeList()) {
      changeListManager.invokeAfterUpdate(new Runnable() {
        public void run() {
          List<Change> changes = new ArrayList<Change>();
          for(FilePath file: files) {
            final Change change = changeListManager.getChange(file);
            if (change != null) {
              changes.add(change);
            }
          }

          changeListManager.moveChangesTo(targetChangeList, changes.toArray(new Change[changes.size()]));
        }
      });
    }
  }

  private static class ApplyPatchMergeRequestFactory implements PatchMergeRequestFactory {
    public static final ApplyPatchMergeRequestFactory INSTANCE = new ApplyPatchMergeRequestFactory();

    public MergeRequest createMergeRequest(final String leftText, final String rightText, final String originalContent, @NotNull final VirtualFile file,
                                           final Project project) {
      MergeRequest request = DiffRequestFactory.getInstance().createMergeRequest(leftText, rightText, originalContent,
                                                                                 file, project, ActionButtonPresentation.createApplyButton());
      request.setVersionTitles(new String[] {
        VcsBundle.message("patch.apply.conflict.local.version"),
        VcsBundle.message("patch.apply.conflict.merged.version"),
        VcsBundle.message("patch.apply.conflict.patched.version")
      });
      request.setWindowTitle(VcsBundle.message("patch.apply.conflict.title", file.getPresentableUrl()));
      return request;
    }
  }
}
