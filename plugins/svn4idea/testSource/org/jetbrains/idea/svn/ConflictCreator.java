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
package org.jetbrains.idea.svn;

import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.diff.impl.patch.formove.PatchApplier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.Convertor;
import junit.framework.Assert;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 5/2/12
 * Time: 2:02 PM
 */
public class ConflictCreator {
  private final Project myProject;
  private final VirtualFile myTheirsDir;
  private final VirtualFile myMineDir;
  private final TreeConflictData.Data myData;
  private final SvnClientRunner myClientRunner;

  public ConflictCreator(final Project project,
                         VirtualFile dir,
                         VirtualFile mineDir,
                         TreeConflictData.Data data,
                         final SvnClientRunner clientRunner) {
    myProject = project;
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
    final List<TextFilePatch> patches = reader.readAllPatches();
    final List<FilePatch> filePatchList = new ArrayList<>(patches);
    for (Iterator<FilePatch> iterator = filePatchList.iterator(); iterator.hasNext(); ) {
      final FilePatch patch = iterator.next();
      if (patch.isDeletedFile()) {
        myClientRunner.delete(myTheirsDir, patch.getBeforeName());
        iterator.remove();
      }
    }

    if (! filePatchList.isEmpty()) {
      PatchApplier<BinaryFilePatch> applier = new PatchApplier<>(myProject, myTheirsDir, filePatchList, (LocalChangeList)null, null, null);
      applier.setIgnoreContentRootsCheck();
      applier.execute();
      Assert.assertEquals(0, applier.getRemainingPatches().size());
    }

    TimeoutUtil.sleep(10);

    SvnVcs vcs = SvnVcs.getInstance(myProject);

    for (TextFilePatch patch : patches) {
      if (patch.isNewFile() || ! Comparing.equal(patch.getAfterName(), patch.getBeforeName())) {
        final String afterName = patch.getAfterName();
        final String[] parts = afterName.split("/");
        String subPath = "";
        for (String part : parts) {
          final String path = subPath + part;
          Info info = vcs.getInfo(new File(myTheirsDir.getPath(), path));
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
    final IOException[] ioe = new IOException[1];
    VfsUtil.processFilesRecursively(myTheirsDir, new Processor<VirtualFile>() {
      @Override
      public boolean process(VirtualFile file) {
        if (myTheirsDir.equals(file)) return true;
        if (file.isDirectory() && file.getChildren().length == 0) {
          try {
            myClientRunner.delete(myTheirsDir, file.getPath());
          }
          catch (IOException e) {
            ioe[0] = e;
          }
        }
        return true;
      }
    }, new Convertor<VirtualFile, Boolean>() {
                                      @Override
                                      public Boolean convert(VirtualFile o) {
                                        return ! SvnUtil.isAdminDirectory(o);
                                      }
                                    });
    /*FileUtil.processFilesRecursively(new File(myTheirsDir.getPath()), new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isDirectory() && file.listFiles().length == 0) {
          try {
            myClientRunner.delete(myTheirsDir, file.getPath());
          }
          catch (IOException e) {
            ioe[0] = e;
          }
        }
        return true;
      }
    });*/
    if (ioe[0] != null) {
      throw ioe[0];
    }

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
