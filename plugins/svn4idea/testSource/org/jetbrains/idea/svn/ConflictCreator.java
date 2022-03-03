// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchReader;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.io.FileUtil.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

// TODO Seems we could just apply corresponding patches with "svn patch" without custom code
public class ConflictCreator {
  private final SvnVcs myVcs;
  private final VirtualFile myTheirsDir;
  private final VirtualFile myMineDir;
  private final TreeConflictData.Data myData;
  private final SvnClientRunner myClientRunner;

  public ConflictCreator(SvnVcs vcs,
                         VirtualFile dir,
                         VirtualFile mineDir,
                         TreeConflictData.Data data,
                         final SvnClientRunner clientRunner) {
    myVcs = vcs;
    myTheirsDir = dir;
    myMineDir = mineDir;
    myData = data;
    myClientRunner = clientRunner;
  }

  public void create() throws PatchSyntaxException, IOException {
    for (TreeConflictData.FileData data : myData.getLeftFiles()) {
      applyFileData(myMineDir, data);
    }

    final List<TextFilePatch> patches = new PatchReader(myData.getTheirsPatch()).readTextPatches();
    final List<FilePatch> filePatchList = new ArrayList<>(patches);
    for (Iterator<FilePatch> iterator = filePatchList.iterator(); iterator.hasNext(); ) {
      final FilePatch patch = iterator.next();
      if (patch.isDeletedFile()) {
        myClientRunner.delete(myTheirsDir, patch.getBeforeName());
        iterator.remove();
      }
    }

    if (!filePatchList.isEmpty()) {
      PatchApplier applier = new PatchApplier(myVcs.getProject(), myTheirsDir, filePatchList, null, null);
      applier.setIgnoreContentRootsCheck();
      applier.execute();
      assertThat(applier.getRemainingPatches(), is(empty()));
    }

    for (TextFilePatch patch : patches) {
      if (patch.isNewFile() || !Objects.equals(patch.getAfterName(), patch.getBeforeName())) {
        String subPath = "";
        for (String part : patch.getAfterName().split("/")) {
          final String path = subPath + part;
          Info info = myVcs.getInfo(new File(myTheirsDir.getPath(), path));
          if (info == null || info.getUrl() == null) {
            myClientRunner.add(myTheirsDir, path);
          }
          subPath = path + "/";
        }
        if (!patch.isNewFile()) {
          myClientRunner.delete(myTheirsDir, patch.getBeforeName());
        }
      }
    }

    VfsUtilCore.visitChildrenRecursively(myTheirsDir, new VirtualFileVisitor<Void>() {
      @NotNull
      @Override
      public Result visitFileEx(@NotNull VirtualFile file) {
        if (!myTheirsDir.equals(file) && file.isDirectory() && file.getChildren().length == 0) {
          try {
            myClientRunner.delete(myTheirsDir, file.getPath());
          }
          catch (IOException e) {
            throw new VisitorException(e);
          }
        }
        return file.isDirectory() && SvnUtil.isAdminDirectory(file) ? SKIP_CHILDREN : CONTINUE;
      }
    }, IOException.class);

    // this will commit all patch changes
    myClientRunner.checkin(myTheirsDir);
    // this will create the conflict
    myClientRunner.update(myMineDir);
    myClientRunner.update(myTheirsDir);
  }

  private void applyFileData(final VirtualFile root, final TreeConflictData.FileData fileData) throws IOException {
    final File target = new File(root.getPath(), fileData.myRelativePath);

    // we don't apply property changes for now
    if (StatusType.STATUS_MISSING.equals(fileData.myNodeStatus)) {
      delete(target);
    }
    else if (StatusType.STATUS_UNVERSIONED.equals(fileData.myNodeStatus)) {
      createFile(fileData, target);
    }
    else if (StatusType.STATUS_ADDED.equals(fileData.myNodeStatus)) {
      if (fileData.myCopyFrom != null) {
        myClientRunner.copyOrMove(root, fileData.myCopyFrom, fileData.myRelativePath, fileData.myIsMove);
      }
      else {
        createFile(fileData, target);
        myClientRunner.add(root, fileData.myRelativePath);
      }
    }
    else if (StatusType.STATUS_DELETED.equals(fileData.myNodeStatus)) {
      myClientRunner.delete(root, fileData.myRelativePath);
    }
    else if (StatusType.STATUS_NORMAL.equals(fileData.myNodeStatus) && StatusType.STATUS_MODIFIED.equals(fileData.myContentsStatus)) {
      createFile(fileData, target);
    }
  }

  private static void createFile(final TreeConflictData.FileData fileData, File target) throws IOException {
    if (fileData.myIsDir) {
      ensureExists(target);
    } else {
      writeToFile(target, fileData.myContents);
    }
  }
}
