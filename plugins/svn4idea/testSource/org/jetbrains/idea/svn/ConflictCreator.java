// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;

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
    // local changes, do not commit
    for (TreeConflictData.FileData data : myData.getLeftFiles()) {
      applyFileData(myMineDir, data);
    }

    final PatchReader reader = new PatchReader(myData.getTheirsPatch());
    final List<TextFilePatch> patches = reader.readTextPatches();
    final List<FilePatch> filePatchList = new ArrayList<>(patches);
    for (Iterator<FilePatch> iterator = filePatchList.iterator(); iterator.hasNext(); ) {
      final FilePatch patch = iterator.next();
      if (patch.isDeletedFile()) {
        myClientRunner.delete(myTheirsDir, patch.getBeforeName());
        iterator.remove();
      }
    }

    if (! filePatchList.isEmpty()) {
      PatchApplier<BinaryFilePatch> applier =
        new PatchApplier<>(myVcs.getProject(), myTheirsDir, filePatchList, (LocalChangeList)null, null);
      applier.setIgnoreContentRootsCheck();
      applier.execute();
      assertEquals(0, applier.getRemainingPatches().size());
    }

    TimeoutUtil.sleep(10);

    for (TextFilePatch patch : patches) {
      if (patch.isNewFile() || ! Comparing.equal(patch.getAfterName(), patch.getBeforeName())) {
        final String afterName = patch.getAfterName();
        final String[] parts = afterName.split("/");
        String subPath = "";
        for (String part : parts) {
          final String path = subPath + part;
          Info info = myVcs.getInfo(new File(myTheirsDir.getPath(), path));
          if (info == null || info.getURL() == null) {
            myClientRunner.add(myTheirsDir, path);
          }
          subPath += part + "/";
        }
        if (! patch.isNewFile()) {
          myClientRunner.delete(myTheirsDir, patch.getBeforeName());
        }
      }
    }

    VfsUtilCore.visitChildrenRecursively(myTheirsDir, new VirtualFileVisitor() {
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

    // we dont apply properties changes fow now
    if (StatusType.STATUS_MISSING.equals(fileData.myNodeStatus)) {
      // delete existing only from fs
      FileUtil.delete(target);
      return;
    } else if (StatusType.STATUS_UNVERSIONED.equals(fileData.myNodeStatus)) {
      // create new unversioned
      createFile(root, fileData, target);
      return;
    } else if (StatusType.STATUS_ADDED.equals(fileData.myNodeStatus)) {
      if (fileData.myCopyFrom != null) {
        myClientRunner.copy(root, fileData.myCopyFrom, fileData.myRelativePath);
        return;
      }
      createFile(root, fileData, target);
      myClientRunner.add(root, fileData.myRelativePath);
      return;
    } else if (StatusType.STATUS_DELETED.equals(fileData.myNodeStatus)) {
      myClientRunner.delete(root, fileData.myRelativePath);
      return;
    } else if (StatusType.STATUS_NORMAL.equals(fileData.myNodeStatus)) {
      if (StatusType.STATUS_MODIFIED.equals(fileData.myContentsStatus)) {
        createFile(root, fileData, target);
        return;
      }
    }
  }

  private void createFile(final VirtualFile root, final TreeConflictData.FileData fileData, File target) throws IOException {
    if (fileData.myIsDir) {
      target.mkdirs();
    } else {
      FileUtil.writeToFile(target, fileData.myContents);
    }
  }
}
