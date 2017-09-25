/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.jetbrains.idea.svn.history.SvnRepositoryLocation;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class SvnCommittedViewTest extends Svn17TestCase {

  @Test
  public void testAdd() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final VirtualFile d1 = createDirInCommand(myWorkingCopyDir, "d1");

    final VirtualFile f11 = createFileInCommand(d1, "f11.txt", "123\n456");
    final VirtualFile f12 = createFileInCommand(d1, "f12.txt", "----");

    // r1, addition without history
    checkin();

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    vcs.invokeRefreshSvnRoots();
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl), 0);
    checkList(changeListList, 1, new Data[] {new Data(absPath(f11), FileStatus.ADDED, null),
      new Data(absPath(f12), FileStatus.ADDED, null), new Data(absPath(d1), FileStatus.ADDED, null)});
  }

  @Test
  public void testDelete() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    final VirtualFile d1 = createDirInCommand(myWorkingCopyDir, "d1");

    final VirtualFile f11 = createFileInCommand(d1, "f11.txt", "123\n456");
    final VirtualFile f12 = createFileInCommand(d1, "f12.txt", "----");

    // r1, addition without history
    checkin();

    deleteFileInCommand(f11);
    
    checkin();
    update();

    deleteFileInCommand(d1);

    checkin();

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    vcs.invokeRefreshSvnRoots();
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl), 0);
    checkList(changeListList, 2, new Data[] {new Data(absPath(f11), FileStatus.DELETED, null)});
    checkList(changeListList, 3, new Data[] {new Data(absPath(d1), FileStatus.DELETED, null)});
  }

  @Test
  public void testReplaced() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    VirtualFile d1 = createDirInCommand(myWorkingCopyDir, "d1");

    VirtualFile f11 = createFileInCommand(d1, "f11.txt", "123\n456");
    VirtualFile f12 = createFileInCommand(d1, "f12.txt", "----");

    // r1, addition without history
    checkin();

    File dir = virtualToIoFile(d1);
    final String d1Path = dir.getAbsolutePath();
    runInAndVerifyIgnoreOutput("delete", d1Path);
    boolean created = dir.mkdir();
    Assert.assertTrue(created);
    runInAndVerifyIgnoreOutput("add", d1Path);

    checkin();

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    vcs.invokeRefreshSvnRoots();
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl), 0);
    checkList(changeListList, 2, new Data[] {new Data(absPath(d1), FileStatus.MODIFIED, "- replaced")});
  }

  @Test
  public void testMoveDir() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    VirtualFile d1 = createDirInCommand(myWorkingCopyDir, "d1");
    VirtualFile d2 = createDirInCommand(myWorkingCopyDir, "d2");

    VirtualFile f11 = createFileInCommand(d1, "f11.txt", "123\n456");
    VirtualFile f12 = createFileInCommand(d1, "f12.txt", "----");

    // r1, addition without history
    checkin();

    final String oldPath = absPath(d1);
    moveFileInCommand(d1, d2);
    Thread.sleep(100);

    checkin();

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    vcs.invokeRefreshSvnRoots();
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl), 0);
    checkList(changeListList, 2, new Data[] {new Data(absPath(d1), FileStatus.MODIFIED, "- moved from .." + File.separatorChar + "d1")});
  }

  @Test
  public void testMoveDirChangeFile() throws Exception {
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);

    VirtualFile d1 = createDirInCommand(myWorkingCopyDir, "d1");
    VirtualFile d2 = createDirInCommand(myWorkingCopyDir, "d2");

    VirtualFile f11 = createFileInCommand(d1, "f11.txt", "123\n456");
    VirtualFile f12 = createFileInCommand(d1, "f12.txt", "----");

    // r1, addition without history
    checkin();

    final String oldPath = absPath(d1);
    final String oldF11Path = virtualToIoFile(f11).getAbsolutePath();
    moveFileInCommand(d1, d2);
    VcsTestUtil.editFileInCommand(myProject, f11, "new");

    Thread.sleep(100);

    checkin();

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    vcs.invokeRefreshSvnRoots();
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl), 0);
    checkList(changeListList, 2, new Data[] {new Data(absPath(d1), FileStatus.MODIFIED, "- moved from .." + File.separatorChar + "d1"),
      new Data(absPath(f11), FileStatus.MODIFIED, "- moved from " + oldF11Path)});
  }

  @Test
  public void testCopyDir() throws Exception {
    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    new File(folder, "f1.txt").createNewFile();
    new File(folder, "f2.txt").createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");
    runInAndVerifyIgnoreOutput("copy", "-m", "test", myRepoUrl + "/trunk", myRepoUrl + "/branch");

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    vcs.invokeRefreshSvnRoots();
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/branch"), 0);
    checkList(changeListList, 2, new Data[] {new Data(new File(myWorkingCopyDir.getPath(), "branch").getAbsolutePath(), FileStatus.ADDED, "- copied from /trunk")});
  }

  @Test
  public void testCopyAndModify() throws Exception {
    final File trunk = new File(myTempDirFixture.getTempDirPath(), "trunk");
    trunk.mkdir();
    Thread.sleep(100);
    final File folder = new File(trunk, "folder");
    folder.mkdir();
    Thread.sleep(100);
    new File(folder, "f1.txt").createNewFile();
    new File(folder, "f2.txt").createNewFile();
    Thread.sleep(100);

    runInAndVerifyIgnoreOutput("import", "-m", "test", trunk.getAbsolutePath(), myRepoUrl + "/trunk");

    update();

    runInAndVerifyIgnoreOutput("copy", myWorkingCopyDir.getPath() + "/trunk", myWorkingCopyDir.getPath() + "/branch");
    runInAndVerifyIgnoreOutput("propset", "testprop", "testval", myWorkingCopyDir.getPath() + "/branch/folder");

    checkin();

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    vcs.invokeRefreshSvnRoots();
    final CommittedChangesProvider<SvnChangeList,ChangeBrowserSettings> committedChangesProvider = vcs.getCommittedChangesProvider();
    final List<SvnChangeList> changeListList =
      committedChangesProvider.getCommittedChanges(committedChangesProvider.createDefaultSettings(),
                                                   new SvnRepositoryLocation(myRepoUrl + "/branch"), 0);
    checkList(changeListList, 2, new Data[] {new Data(new File(myWorkingCopyDir.getPath(), "branch").getAbsolutePath(), FileStatus.ADDED, "- copied from /trunk"),
      new Data(new File(myWorkingCopyDir.getPath(), "branch/folder").getAbsolutePath(), FileStatus.MODIFIED, "- copied from /trunk/folder")});
  }

  protected String absPath(final VirtualFile vf) {
    return virtualToIoFile(vf).getAbsolutePath();
  }

  protected static class Data {
    public final String myLocalPath;
    public final FileStatus myStatus;
    @Nullable
    public final String myOriginText;

    protected Data(@NotNull final String localPath, @NotNull final FileStatus status, @Nullable final String originText) {
      myLocalPath = localPath;
      myStatus = status;
      myOriginText = originText;
    }

    public boolean shouldBeComparedWithChange(final Change change) {
      if (FileStatus.DELETED.equals(myStatus) && (change.getAfterRevision() == null)) {
        // before path
        return (change.getBeforeRevision() != null) && myLocalPath.equals(change.getBeforeRevision().getFile().getPath());
      } else {
        return (change.getAfterRevision() != null) && myLocalPath.equals(change.getAfterRevision().getFile().getPath());
      }
    }
  }

  protected void checkList(final List<SvnChangeList> lists, final long revision, final Data[] content) {
    SvnChangeList list = null;
    for (SvnChangeList changeList : lists) {
      if (changeList.getNumber() == revision) {
        list = changeList;
      }
    }
    Assert.assertNotNull("Change list #" + revision + " not found.", list);

    final Collection<Change> changes = new ArrayList<>(list.getChanges());
    Assert.assertNotNull("Null changes list", changes);
    Assert.assertEquals(changes.size(), content.length);

    for (Data data : content) {
      boolean found = false;
      for (Change change : changes) {
        if (data.shouldBeComparedWithChange(change)) {
          Assert.assertTrue(Comparing.equal(data.myOriginText, change.getOriginText(myProject)));
          Assert.assertEquals(data.myStatus, change.getFileStatus());
          found = true;
          break;
        }
      }
      Assert.assertTrue(printChanges(data, changes), found);
    }
  }

  private static String printChanges(final Data data, final Collection<Change> changes) {
    final StringBuilder sb = new StringBuilder("Data: ").append(data.myLocalPath).append(" exists: ").
      append(new File(data.myLocalPath).exists()).append(" Changes: ");
    for (Change change : changes) {
      final ContentRevision cr = change.getAfterRevision() == null ? change.getBeforeRevision() : change.getAfterRevision();
      final File ioFile = cr.getFile().getIOFile();
      sb.append("'").append(ioFile.getAbsolutePath()).append("' exists: ").append(ioFile.exists()).append(" | ");
    }
    return sb.toString();
  }
}
