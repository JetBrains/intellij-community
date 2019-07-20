// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.treeConflict;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchExecutor;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;
import com.intellij.openapi.vcs.changes.patch.TextFilePatchInProgress;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.CommonBundle.getErrorTitle;
import static com.intellij.openapi.util.io.FileUtil.getRelativePath;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.util.ObjectUtils.notNull;

public class ApplyPatchSaveToFileExecutor implements ApplyPatchExecutor<TextFilePatchInProgress> {
  private static final Logger LOG = Logger.getInstance(ApplyPatchSaveToFileExecutor.class);

  @NotNull private final Project myProject;
  @Nullable private final VirtualFile myNewPatchBase;

  public ApplyPatchSaveToFileExecutor(@NotNull Project project, @Nullable VirtualFile newPatchBase) {
    myProject = project;
    myNewPatchBase = newPatchBase;
  }

  @Override
  public String getName() {
    return "Save Patch to File";
  }

  @Override
  public void apply(@NotNull List<? extends FilePatch> remaining,
                    @NotNull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable ThrowableComputable<? extends Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(new FileSaverDescriptor("Save Patch to", ""), myProject);
    VirtualFileWrapper targetFile = dialog.save(myProject.getBaseDir(), "TheirsChanges.patch");

    if (targetFile != null) {
      savePatch(patchGroupsToApply, targetFile);
    }
  }

  private void savePatch(@NotNull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroups, @NotNull VirtualFileWrapper targetFile) {
    VirtualFile newPatchBase = notNull(myNewPatchBase, myProject.getBaseDir());
    try {
      List<FilePatch> textPatches = toOnePatchGroup(patchGroups, newPatchBase);
      PatchWriter.writePatches(myProject, targetFile.getFile().getPath(), newPatchBase.getPath(), textPatches, new CommitContext(),
                               StandardCharsets.UTF_8);
    }
    catch (IOException e) {
      LOG.info(e);
      WaitForProgressToShow.runOrInvokeLaterAboveProgress(
        () -> Messages.showErrorDialog(myProject, message("create.patch.error.title", e.getMessage()), getErrorTitle()), null, myProject);
    }
  }

  @NotNull
  public static List<FilePatch> toOnePatchGroup(@NotNull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroups,
                                                @NotNull VirtualFile newPatchBase) throws IOException {
    List<FilePatch> result = new ArrayList<>();

    for (Map.Entry<VirtualFile, Collection<TextFilePatchInProgress>> entry : patchGroups.entrySet()) {
      VirtualFile oldPatchBase = entry.getKey();
      String relativePath = VfsUtilCore.getRelativePath(oldPatchBase, newPatchBase, '/');
      boolean toConvert = !isEmptyOrSpaces(relativePath) && !".".equals(relativePath);

      for (TextFilePatchInProgress patchInProgress : entry.getValue()) {
        TextFilePatch patch = patchInProgress.getPatch();
        if (toConvert) {
          patch.setBeforeName(getNewBaseRelativePath(newPatchBase, oldPatchBase, patch.getBeforeName()));
          patch.setAfterName(getNewBaseRelativePath(newPatchBase, oldPatchBase, patch.getAfterName()));
        }
        result.add(patch);
      }
    }

    return result;
  }

  @Nullable
  private static String getNewBaseRelativePath(@NotNull VirtualFile newBase,
                                               @NotNull VirtualFile oldBase,
                                               @Nullable String oldBaseRelativePath) throws IOException {
    return !isEmptyOrSpaces(oldBaseRelativePath)
           ? getRelativePath(newBase.getPath(), getCanonicalPath(oldBase, oldBaseRelativePath), '/')
           : oldBaseRelativePath;
  }

  @NotNull
  private static String getCanonicalPath(@NotNull VirtualFile base, @NotNull String relativePath) throws IOException {
    return toSystemIndependentName(new File(base.getPath(), relativePath).getCanonicalPath());
  }
}
