/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsTestUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.SvnTestCase;
import org.jetbrains.idea.svn.branchConfig.InfoReliability;
import org.jetbrains.idea.svn.branchConfig.InfoStorage;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager;
import org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationNew;
import org.jetbrains.idea.svn.dialogs.*;
import org.jetbrains.idea.svn.branchConfig.SvnBranchItem;
import org.jetbrains.idea.svn.integrate.MergeContext;
import org.jetbrains.idea.svn.integrate.QuickMerge;
import org.jetbrains.idea.svn.integrate.QuickMergeContentsVariants;
import org.jetbrains.idea.svn.mergeinfo.MergeChecker;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 3/27/13
 * Time: 12:58 PM
 */
public class SvnQuickMergeTest extends Svn17TestCase {
  private SvnVcs myVcs;
  private String myBranchUrl;
  private File myBranchRoot;
  private VirtualFile myBranchVf;
  private SubTree myBranchTree;
  private ChangeListManager myChangeListManager;
  private SvnTestCase.SubTree myTree;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    myVcs = SvnVcs.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myBranchUrl = prepareBranchesStructure();
    myBranchRoot = new File(myTempDirFixture.getTempDirPath(), "b1");

    runInAndVerifyIgnoreOutput("co", myBranchUrl, myBranchRoot.getPath());
    Assert.assertTrue(myBranchRoot.exists());
    myBranchVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myBranchRoot);
    Assert.assertNotNull(myBranchVf);

    myBranchTree = new SubTree(myBranchVf);
    myTree = new SubTree(myWorkingCopyDir);

    final SvnBranchConfigurationManager branchConfigurationManager = SvnBranchConfigurationManager.getInstance(myProject);
    final SvnBranchConfigurationNew configuration = new SvnBranchConfigurationNew();
    configuration.setTrunkUrl(myRepoUrl + "/trunk");
    configuration.addBranches(myRepoUrl + "/branches",
                              new InfoStorage<>(new ArrayList<>(), InfoReliability.empty));
    branchConfigurationManager.setConfiguration(myWorkingCopyDir, configuration);

    //((ApplicationImpl) ApplicationManager.getApplication()).setRunPooledInTest(true);

    runInAndVerifyIgnoreOutput(new File(myWorkingCopyDir.getPath()), "up");
    Thread.sleep(10);
  }

  @Test
  public void testSimpleMergeAllFromB1ToTrunk() throws Exception {
    VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, "edited in branch");
    runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch", myBranchTree.myS1File.getPath());

    final QuickMerge quickMerge = newQuickMerge(myBranchUrl);
    // by default merges all
    final QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction() {
      @Override
      public boolean shouldReintegrate(@NotNull String sourceUrl, @NotNull String targetUrl) {
        return true;
      }
    };
    final WaitingTaskDescriptor descriptor = new WaitingTaskDescriptor();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        quickMerge.execute(testInteraction, descriptor);
      }
    });
    descriptor.waitForCompletion();
    testInteraction.throwIfExceptions();

    Assert.assertTrue(descriptor.isCompleted());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS1File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());
  }

  // if we create branches like this:
  // trunk -> b1, b1->b2, b2->b3, b1->b4, then we should be able to merge between b1 and b2. some time before we had bug with it
  @Test
  public void testMergeBetweenDifferentTimeCreatedBranches() throws Exception {
    // b1 -> b2
    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "copy1", myBranchUrl, myRepoUrl + "/branches/b2");
    // b2 -> b3
    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "copy1", myRepoUrl + "/branches/b2", myRepoUrl + "/branches/b3");
    // b1 -> b4
    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "copy1", myBranchUrl, myRepoUrl + "/branches/b4");

    testSimpleMergeAllFromB1ToTrunk();
  }

  @Test
  public void testSelectRevisionsWithQuickSelectCheckForLocalChanges() throws Exception {
    // get revision #
    final SVNInfo info = myVcs.getSvnKitManager().createWCClient().doInfo(new File(myBranchTree.myS1File.getPath()), SVNRevision.WORKING);
    Assert.assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 3;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(new File(myBranchTree.myS1File.getPath())));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
      Thread.sleep(10);
    }

    // we should get exactly 2 revisions for selection (copy and change in b2)
    final QuickMerge quickMerge = newQuickMerge(myBranchUrl);
    // by default merges all
    final AtomicReference<String> selectionError = new AtomicReference<>();
    final QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction() {
      @Override
      public boolean shouldReintegrate(@NotNull String sourceUrl, @NotNull String targetUrl) {
        return true;
      }

      @Override
      public List<CommittedChangeList> showRecentListsForSelection(@NotNull List<CommittedChangeList> list,
                                                                   @NotNull String mergeTitle,
                                                                   @NotNull MergeChecker mergeChecker,
                                                                   @NotNull PairConsumer<Long, MergeDialogI> loader,
                                                                   boolean everyThingLoaded) {
        if (list.size() != 4) {
          selectionError.set("List size: " + list.size());
        } else if (list.get(3).getNumber() != numberBefore) {
          selectionError.set("wrong revision for copy statement: " + list.get(3).getNumber());
        }
        return new SmartList<>(list.get(2));  // get a change
      }
    };
    testInteraction.setMergeVariant(QuickMergeContentsVariants.showLatest);
    final WaitingTaskDescriptor descriptor = new WaitingTaskDescriptor();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        quickMerge.execute(testInteraction, descriptor);
      }
    });
    descriptor.waitForCompletion();
    testInteraction.throwIfExceptions();
    if (selectionError.get() != null){
      throw new RuntimeException(selectionError.get());
    }

    Assert.assertTrue(descriptor.isCompleted());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS1File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    final SVNPropertyData data = myVcs.getSvnKitManager().createWCClient()
      .doGetProperty(new File(myWorkingCopyDir.getPath()), "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    System.out.println(data.getValue().getString());
    Assert.assertEquals("/branches/b1:" + (numberBefore + 1), data.getValue().getString());
  }

  // this test is mainly to check revisions selection. at the moment we are not sure whether we support
  // trunk->b1->b2 merges between trunk and b2
  @Test
  public void testSelectRevisionsWithQuickSelect() throws Exception {
    // get revision #
    final SVNInfo info = myVcs.getSvnKitManager().createWCClient().doInfo(new File(myBranchTree.myS1File.getPath()), SVNRevision.WORKING);
    Assert.assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 3;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(new File(myBranchTree.myS1File.getPath())));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
      Thread.sleep(10);
    }

    // before copy
    final SVNInfo info2 = myVcs.getSvnKitManager().createWCClient().doInfo(new File(myBranchTree.myS1File.getPath()), SVNRevision.WORKING);
    Assert.assertNotNull(info2);
    final long numberBeforeCopy = info2.getRevision().getNumber();

    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "copy1", myBranchUrl, myRepoUrl + "/branches/b2");

    // switch b1 to b2
    runInAndVerifyIgnoreOutput(myBranchRoot, "switch", myRepoUrl + "/branches/b2", myBranchRoot.getPath());
    myBranchTree = new SubTree(myBranchVf); //reload

    // one commit in b2 in s2 file
    VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS2File, "completely changed");
    runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in b2", myBranchTree.myS2File.getPath());

    // we should get exactly 2 revisions for selection (copy and change in b2)
    final QuickMerge quickMerge = newQuickMerge(myRepoUrl + "/branches/b2");
    // by default merges all
    final AtomicReference<String> selectionError = new AtomicReference<>();
    final QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction() {
      @Override
      public boolean shouldReintegrate(@NotNull String sourceUrl, @NotNull String targetUrl) {
        return true;
      }

      @Override
      public List<CommittedChangeList> showRecentListsForSelection(@NotNull List<CommittedChangeList> list,
                                                                   @NotNull String mergeTitle,
                                                                   @NotNull MergeChecker mergeChecker,
                                                                   @NotNull PairConsumer<Long, MergeDialogI> loader,
                                                                   boolean everyThingLoaded) {
        if (list.size() != 2) {
          selectionError.set("List size: " + list.size());
        } else if (list.get(1).getNumber() != numberBeforeCopy + 1) {
          selectionError.set("wrong revision for copy statement: " + list.get(1).getNumber());
        }
        return new SmartList<>(list.get(0));  // get a change
      }
    };
    testInteraction.setMergeVariant(QuickMergeContentsVariants.showLatest);
    final WaitingTaskDescriptor descriptor = new WaitingTaskDescriptor();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        quickMerge.execute(testInteraction, descriptor);
      }
    });
    descriptor.waitForCompletion();
    testInteraction.throwIfExceptions();
    if (selectionError.get() != null){
      throw new RuntimeException(selectionError.get());
    }

    Assert.assertTrue(descriptor.isCompleted());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS2File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    final SVNPropertyData data = myVcs.getSvnKitManager().createWCClient()
      .doGetProperty(new File(myWorkingCopyDir.getPath()), "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    System.out.println(data.getValue().getString());
    Assert.assertEquals("/branches/b2:" + (numberBeforeCopy + 2), data.getValue().getString());
  }

  @Test
  public void testSelectRevisions() throws Exception {
    // get revision #
    final SVNInfo info = myVcs.getSvnKitManager().createWCClient().doInfo(new File(myBranchTree.myS1File.getPath()), SVNRevision.WORKING);
    Assert.assertNotNull(info);

    final long numberBefore = info.getRevision().getNumber();
    final int totalChanges = 10;

    final StringBuilder sb = new StringBuilder(FileUtil.loadFile(new File(myBranchTree.myS1File.getPath())));
    for (int i = 0; i < totalChanges; i++) {
      sb.append("\nedited in branch ").append(i);
      VcsTestUtil.editFileInCommand(myProject, myBranchTree.myS1File, sb.toString());
      runInAndVerifyIgnoreOutput(myBranchRoot, "ci", "-m", "change in branch " + i, myBranchTree.myS1File.getPath());
      Thread.sleep(10);
    }

    final QuickMerge quickMerge = newQuickMerge(myBranchUrl);
    // by default merges all
    final QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction() {
      @Override
      public boolean shouldReintegrate(@NotNull String sourceUrl, @NotNull String targetUrl) {
        return true;
      }

      @NotNull
      @Override
      public SelectMergeItemsResult selectMergeItems(final List<CommittedChangeList> lists,
                                                     String mergeTitle,
                                                     MergeChecker mergeChecker) {
        return new SelectMergeItemsResult() {
          @Override
          public QuickMergeContentsVariants getResultCode() {
            return QuickMergeContentsVariants.select;
          }

          @Override
          public List<CommittedChangeList> getSelectedLists() {
            final List<CommittedChangeList> result = new ArrayList<>();
            for (CommittedChangeList list : lists) {
              if (numberBefore + 1 == list.getNumber() || numberBefore + 2 == list.getNumber()) {
                result.add(list);
              }
            }
            return result;
          }
        };
      }
    };
    testInteraction.setMergeVariant(QuickMergeContentsVariants.select);
    final WaitingTaskDescriptor descriptor = new WaitingTaskDescriptor();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        quickMerge.execute(testInteraction, descriptor);
      }
    });
    descriptor.waitForCompletion();
    testInteraction.throwIfExceptions();

    Assert.assertTrue(descriptor.isCompleted());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS1File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());

    final SVNPropertyData data = myVcs.getSvnKitManager().createWCClient()
      .doGetProperty(new File(myWorkingCopyDir.getPath()), "svn:mergeinfo", SVNRevision.UNDEFINED, SVNRevision.WORKING);
    System.out.println(data.getValue().getString());
    Assert.assertEquals("/branches/b1:" + (numberBefore + 1) + "-" + (numberBefore + 2), data.getValue().getString());
  }

  private WCInfo getWcInfo() {
    WCInfo found = null;
    final File workingIoFile = new File(myWorkingCopyDir.getPath());
    final List<WCInfo> infos = myVcs.getAllWcInfos();
    for (WCInfo info : infos) {
      if (FileUtil.filesEqual(workingIoFile, new File(info.getPath()))) {
        found = info;
        break;
      }
    }
    Assert.assertNotNull(found);
    return found;
  }

  @Test
  public void testSimpleMergeFromTrunkToB1() throws Exception {
    // change in trunk
    VcsTestUtil.editFileInCommand(myProject, myTree.myS1File, "903403240328");
    final File workingIoFile = new File(myWorkingCopyDir.getPath());
    runInAndVerifyIgnoreOutput(workingIoFile, "ci", "-m", "change in trunk", myTree.myS1File.getPath());

    final String trunkUrl = myRepoUrl + "/trunk";
    // switch this copy to b1
    runInAndVerifyIgnoreOutput(workingIoFile, "switch", myBranchUrl, workingIoFile.getPath());
    myTree = new SubTree(myWorkingCopyDir); //reload

    refreshSvnMappingsSynchronously();
    final QuickMerge quickMerge = newQuickMerge(trunkUrl);
    // by default merges all
    final QuickMergeTestInteraction testInteraction = new QuickMergeTestInteraction();
    final WaitingTaskDescriptor descriptor = new WaitingTaskDescriptor();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        quickMerge.execute(testInteraction, descriptor);
      }
    });
    descriptor.waitForCompletion();
    testInteraction.throwIfExceptions();

    Assert.assertTrue(descriptor.isCompleted());

    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    // should have changed svn:mergeinfo on wc root and s1 file
    final Change fileChange = myChangeListManager.getChange(myTree.myS1File);
    Assert.assertNotNull(fileChange);
    Assert.assertEquals(FileStatus.MODIFIED, fileChange.getFileStatus());

    final Change dirChange = myChangeListManager.getChange(myWorkingCopyDir);
    Assert.assertNotNull(dirChange);
    Assert.assertEquals(FileStatus.MODIFIED, dirChange.getFileStatus());
  }

  private QuickMerge newQuickMerge(@NotNull String sourceUrl) {
    MergeContext mergeContext = new MergeContext(myVcs, sourceUrl, getWcInfo(), SVNPathUtil.tail(sourceUrl), myWorkingCopyDir);

    return new QuickMerge(mergeContext);
  }

  private static class WaitingTaskDescriptor extends TaskDescriptor {
    private static final long TEST_TIMEOUT = TimeUnit.MINUTES.toMillis(200);
    private final Semaphore mySemaphore;
    private volatile boolean myCompleted = false;
    private volatile boolean myCanceled = false;

    public WaitingTaskDescriptor() {
      super("waiting", Where.POOLED);
      mySemaphore = new Semaphore();
      mySemaphore.down();
    }

    // will survive in Continuation if cancel occurred
    @Override
    public boolean isHaveMagicCure() {
      return true;
    }

    @Override
    public void run(ContinuationContext context) {
      myCompleted = true;
      mySemaphore.up();
    }

    public void waitForCompletion() {
      mySemaphore.waitFor(TEST_TIMEOUT);
    }

    @Override
    public void canceled() {
      myCanceled = true;
      mySemaphore.up();
    }

    private boolean isCompleted() {
      return myCompleted;
    }

    private boolean isCanceled() {
      return myCanceled;
    }
  }
}
