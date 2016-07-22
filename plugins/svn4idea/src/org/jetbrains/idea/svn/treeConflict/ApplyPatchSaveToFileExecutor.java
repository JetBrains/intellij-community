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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.TransparentlyFailedValueI;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchExecutor;
import com.intellij.openapi.vcs.changes.patch.PatchWriter;
import com.intellij.openapi.vcs.changes.patch.TextFilePatchInProgress;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/17/12
 * Time: 6:02 PM
 */
public class ApplyPatchSaveToFileExecutor implements ApplyPatchExecutor<TextFilePatchInProgress> {
  private static final Logger LOG = Logger.getInstance(ApplyPatchSaveToFileExecutor.class);

  private final Project myProject;
  private final VirtualFile myBaseForPatch;

  public ApplyPatchSaveToFileExecutor(Project project, VirtualFile baseForPatch) {
    myProject = project;
    myBaseForPatch = baseForPatch;
  }

  @Override
  public String getName() {
    return "Save Patch to File";
  }

  @Override
  public void apply(@NotNull List<FilePatch> remaining, @NotNull MultiMap<VirtualFile, TextFilePatchInProgress> patchGroupsToApply,
                    @Nullable LocalChangeList localList,
                    @Nullable String fileName,
                    @Nullable TransparentlyFailedValueI<Map<String, Map<String, CharSequence>>, PatchSyntaxException> additionalInfo) {
    final FileSaverDialog dialog = FileChooserFactory.getInstance().createSaveFileDialog(
      new FileSaverDescriptor("Save Patch to", ""), myProject);
    final VirtualFile baseDir = myProject.getBaseDir();
    final VirtualFileWrapper save = dialog.save(baseDir, "TheirsChanges.patch");
    if (save != null) {
      final CommitContext commitContext = new CommitContext();

      final VirtualFile baseForPatch = myBaseForPatch == null ? baseDir : myBaseForPatch;
      try {
        final List<FilePatch> textPatches = patchGroupsToOneGroup(patchGroupsToApply, baseForPatch);
        PatchWriter.writePatches(myProject, save.getFile().getPath(), baseForPatch.getPath(), textPatches, commitContext,
                                 CharsetToolkit.UTF8_CHARSET);
      }
      catch (final IOException e) {
        LOG.info(e);
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", e.getMessage()), CommonBundle.getErrorTitle());
          }
        }, null, myProject);
      }
    }
  }

  public static List<FilePatch> patchGroupsToOneGroup(MultiMap<VirtualFile, TextFilePatchInProgress> patchGroups, VirtualFile baseDir)
    throws IOException {
    final List<FilePatch> textPatches = new ArrayList<>();
    final String baseDirPath = baseDir.getPath();

    for (Map.Entry<VirtualFile, Collection<TextFilePatchInProgress>> entry : patchGroups.entrySet()) {
      final VirtualFile vf = entry.getKey();
      final String currBasePath = vf.getPath();
      final String relativePath = VfsUtilCore.getRelativePath(vf, baseDir, '/');
      final boolean toConvert = !StringUtil.isEmptyOrSpaces(relativePath) && !".".equals(relativePath);
      for (TextFilePatchInProgress patchInProgress : entry.getValue()) {
        final TextFilePatch patch = patchInProgress.getPatch();
        if (toConvert) {
          //correct paths
          patch.setBeforeName(convertRelativePath(patch.getBeforeName(), currBasePath, baseDirPath));
          patch.setAfterName(convertRelativePath(patch.getAfterName(), currBasePath, baseDirPath));
        }
        textPatches.add(patch);
      }
    }
    return textPatches;
  }

  private static String convertRelativePath(String pathInPatch, String currentBase, String baseDirPath) throws IOException {
    if (StringUtil.isEmptyOrSpaces(pathInPatch)) return pathInPatch;
    final File currentPath = new File(currentBase, pathInPatch);
    return FileUtil.getRelativePath(FileUtil.toSystemIndependentName(baseDirPath), FileUtil.toSystemIndependentName(currentPath.getCanonicalPath()), '/');
  }
}
