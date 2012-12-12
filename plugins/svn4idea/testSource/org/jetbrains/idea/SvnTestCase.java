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
package org.jetbrains.idea;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase;
import com.intellij.testFramework.vcs.MockChangeListManagerGate;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.testFramework.vcs.TestClientRunner;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnFileUrlMappingImpl;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.CreateExternalAction;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author yole
 */
public abstract class SvnTestCase extends AbstractJunitVcsTestCase  {
  protected TempDirTestFixture myTempDirFixture;
  protected String myRepoUrl;
  protected TestClientRunner myRunner;
  protected String myWcRootName;

  private final String myTestDataDir;
  private File myRepoRoot;
  private File myWcRoot;
  private ChangeListManagerGate myGate;

  protected SvnTestCase(@NotNull String testDataDir) {
    PlatformTestCase.initPlatformLangPrefix();
    myTestDataDir = testDataDir;
    myWcRootName = "wcroot";
  }

  public static void imitateEvent(VirtualFile dir) {
    final VirtualFile child = dir.findChild(".svn");
    org.junit.Assert.assertNotNull(child);
    final VirtualFile wcdb = child.findChild("wc.db");
    org.junit.Assert.assertNotNull(wcdb);

    final BulkFileListener listener = ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
    final VFileContentChangeEvent event =
      new VFileContentChangeEvent(null, wcdb, wcdb.getModificationStamp() - 1, wcdb.getModificationStamp(), true);
    final List<VFileContentChangeEvent> events = Collections.singletonList(event);
    listener.before(events);
    listener.after(events);
  }

  @Override
  protected String getPluginName() {
    return "Subversion";
  }

  @Before
  public void setUp() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
          myTempDirFixture = fixtureFactory.createTempDirTestFixture();
          myTempDirFixture.setUp();

          myRepoRoot = new File(myTempDirFixture.getTempDirPath(), "svnroot");
          assert myRepoRoot.mkdir() || myRepoRoot.isDirectory() : myRepoRoot;

          File pluginRoot = new File(PluginPathManager.getPluginHomePath("svn4idea"));
          if (!pluginRoot.isDirectory()) {
            // try standalone mode
            Class aClass = SvnTestCase.class;
            String rootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
            pluginRoot = new File(rootPath).getParentFile().getParentFile().getParentFile();
          }

          File svnBinDir =  new File(pluginRoot, myTestDataDir + "/svn/bin");
          File svnExecutable = null;
          if (SystemInfo.isWindows) {
            svnExecutable = new File(svnBinDir, "windows/svn.exe");
          }
          else if (SystemInfo.isLinux) {
            svnExecutable = new File(svnBinDir, "linux/svn");
          }
          else if (SystemInfo.isMac) {
            svnExecutable = new File(svnBinDir, "mac/svn");
          }
          assertTrue("No Subversion executable was found: " + svnExecutable + ", " + SystemInfo.OS_NAME,
                     svnExecutable != null && svnExecutable.canExecute());
          myClientBinaryPath = svnExecutable.getParentFile();
          myRunner = SystemInfo.isMac
                     ? createClientRunner(Collections.singletonMap("DYLD_LIBRARY_PATH", myClientBinaryPath.getPath()))
                     : createClientRunner();

          ZipUtil.extract(new File(pluginRoot, myTestDataDir + "/svn/newrepo.zip"), myRepoRoot, null);

          myWcRoot = new File(myTempDirFixture.getTempDirPath(), myWcRootName);
          assert myWcRoot.mkdir() || myWcRoot.isDirectory() : myWcRoot;

          myRepoUrl = "file:///" + FileUtil.toSystemIndependentName(myRepoRoot.getPath());

          initProject(myWcRoot, SvnTestCase.this.getTestName());
          activateVCS(SvnVcs.VCS_NAME);

          verify(runSvn("co", myRepoUrl, myWorkingCopyDir.getPath()));

          myGate = new MockChangeListManagerGate(ChangeListManager.getInstance(myProject));

          final SvnVcs vcs = SvnVcs.getInstance(myProject);
          ((StartupManagerImpl) StartupManager.getInstance(myProject)).runPostStartupActivities();
          ((SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping()).realRefresh();

        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    // there should be kind-a waiting for after change list manager finds all changes and runs inner refresh of copies in the above method
    if (myInitChangeListManager) {
      ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      changeListManager.ensureUpToDate(false);
    }
  }

  @After
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          tearDownProject();

          if (myWcRoot != null && myWcRoot.exists()) {
            FileUtil.delete(myWcRoot);
          }
          if (myRepoRoot != null && myRepoRoot.exists()) {
            FileUtil.delete(myRepoRoot);
          }

          if (myTempDirFixture != null) {
            myTempDirFixture.tearDown();
            myTempDirFixture = null;
          }
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  protected ProcessOutput runSvn(String... commandLine) throws IOException {
    return myRunner.runClient("svn", null, myWcRoot, commandLine);
  }

  protected void enableSilentOperation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(SvnVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
  }

  protected void disableSilentOperation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(SvnVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
  }

  protected void checkin() throws IOException {
    verify(runSvn("ci", "-m", "test"));
  }

  protected void update() throws IOException {
    verify(runSvn("up"));
  }

  protected List<Change> getChangesInScope(final VcsDirtyScope dirtyScope) throws VcsException {
    ChangeProvider changeProvider = SvnVcs.getInstance(myProject).getChangeProvider();
    MockChangelistBuilder builder = new MockChangelistBuilder();
    changeProvider.getChanges(dirtyScope, builder, new EmptyProgressIndicator(), myGate);
    return builder.getChanges();
  }

  protected void undo() {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        final TestDialog oldTestDialog = Messages.setTestDialog(TestDialog.OK);
        try {
          UndoManager.getInstance(myProject).undo(null);
        }
        finally {
          Messages.setTestDialog(oldTestDialog);
        }
      }
    });
  }

  protected class SubTree {
    public final VirtualFile myRootDir;
    public VirtualFile mySourceDir;
    public final VirtualFile myTargetDir;

    public VirtualFile myS1File;
    public VirtualFile myS2File;

    public final List<VirtualFile> myTargetFiles;
    public static final String ourS1Contents = "123";
    public static final String ourS2Contents = "abc";

    public SubTree(final VirtualFile base) throws Exception {
      myRootDir = createDirInCommand(base, "root");
      mySourceDir = createDirInCommand(myRootDir, "source");
      myS1File = createFileInCommand(mySourceDir, "s1.txt", ourS1Contents);
      myS2File = createFileInCommand(mySourceDir, "s2.txt", ourS2Contents);

      myTargetDir = createDirInCommand(myRootDir, "target");
      myTargetFiles = new ArrayList<VirtualFile>();
      for (int i = 0; i < 10; i++) {
        myTargetFiles.add(createFileInCommand(myTargetDir, "t" + (i+10) +".txt", ourS1Contents));
      }
    }
  }

  protected static void sleep(final int millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ignore) { }
  }

  public void prepareExternal() throws Exception {
    final ChangeListManagerImpl clManager = (ChangeListManagerImpl)ChangeListManager.getInstance(myProject);
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final String mainUrl = myRepoUrl + "/root/source";
    final String externalURL = myRepoUrl + "/root/target";

    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();
    clManager.stopEveryThingIfInTestMode();
    sleep(100);
    final File rootFile = new File(subTree.myRootDir.getPath());
    FileUtil.delete(rootFile);
    FileUtil.delete(new File(myWorkingCopyDir.getPath() + File.separator + ".svn"));
    Assert.assertTrue(!rootFile.exists());
    sleep(200);
    myWorkingCopyDir.refresh(false, true);

    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    verify(runSvn("co", mainUrl, sourceDir.getPath()));
    CreateExternalAction.addToExternalProperty(vcs, sourceDir, "external", externalURL);
    sleep(100);
    verify(runSvn("up", sourceDir.getPath()));
    verify(runSvn("ci", "-m", "test", sourceDir.getPath()));
    sleep(100);
    myWorkingCopyDir.refresh(false, true);
    Assert.assertTrue(new File(sourceDir, "external").exists());
    // above is preparation

    // start change list manager again
    clManager.forceGoInTestMode();
    SvnConfiguration.getInstance(myProject).DETECT_NESTED_COPIES = true;
    vcs.invokeRefreshSvnRoots(false);
    clManager.ensureUpToDate(false);
    clManager.ensureUpToDate(false);
  }
}
