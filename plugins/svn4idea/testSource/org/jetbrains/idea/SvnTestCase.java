// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase;
import com.intellij.testFramework.vcs.MockChangeListManagerGate;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.testFramework.vcs.TestClientRunner;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnFileUrlMappingImpl;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.CreateExternalAction;
import org.jetbrains.idea.svn.api.Url;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.junit.Assert.*;

public abstract class SvnTestCase extends AbstractJunitVcsTestCase  {

  public static String ourGlobalTestDataDir;

  protected TempDirTestFixture myTempDirFixture;
  protected Url myRepositoryUrl;
  protected String myRepoUrl;
  protected TestClientRunner myRunner;
  protected String myWcRootName;

  private String myTestDataDir;
  private File myRepoRoot;
  private File myWcRoot;
  private ChangeListManagerGate myGate;
  protected String myAnotherRepoUrl;
  protected File myPluginRoot;

  protected SvnTestCase(@NotNull String testDataDir) {
    myTestDataDir = testDataDir;
    myWcRootName = "wcroot";
  }

  public static void imitateEvent(VirtualFile dir) {
    final VirtualFile child = dir.findChild(".svn");
    assertNotNull(child);
    final VirtualFile wcdb = child.findChild("wc.db");
    assertNotNull(wcdb);

    final BulkFileListener listener = ApplicationManager.getApplication().getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES);
    final VFileContentChangeEvent event =
      new VFileContentChangeEvent(null, wcdb, wcdb.getModificationStamp() - 1, wcdb.getModificationStamp(), true);
    final List<VFileContentChangeEvent> events = Collections.singletonList(event);
    listener.before(events);
    listener.after(events);
  }

  @Before
  public void setUp() throws Exception {
    String property = System.getProperty("svn.test.data.directory");
    if (!StringUtil.isEmpty(property)) {
      myTestDataDir = property;
    }

    runInEdtAndWait(() -> {
      final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
      myTempDirFixture = fixtureFactory.createTempDirTestFixture();
      myTempDirFixture.setUp();

      myRepoRoot = new File(myTempDirFixture.getTempDirPath(), "svnroot");
      boolean isRepoRootCreated = myRepoRoot.mkdir() || myRepoRoot.isDirectory();
      assert isRepoRootCreated : myRepoRoot;

      myPluginRoot = new File(PluginPathManager.getPluginHomePath("svn4idea"));
      if (!myPluginRoot.isDirectory()) {
        // try standalone mode
        Class aClass = SvnTestCase.class;
        String rootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
        myPluginRoot = new File(rootPath).getParentFile().getParentFile().getParentFile();
      }

      File svnBinDir = new File(myPluginRoot, getTestDataDir() + "/svn/bin");
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

      ZipUtil.extract(new File(myPluginRoot, getTestDataDir() + "/svn/newrepo.zip"), myRepoRoot, null);

      myWcRoot = new File(myTempDirFixture.getTempDirPath(), myWcRootName);
      boolean isWcRootCreated = myWcRoot.mkdir() || myWcRoot.isDirectory();
      assert isWcRootCreated : myWcRoot;

      myRepoUrl = (SystemInfo.isWindows ? "file:///" : "file://") + FileUtil.toSystemIndependentName(myRepoRoot.getPath());
      myRepositoryUrl = parseUrl(myRepoUrl);

      verify(runSvn("co", myRepoUrl, myWcRoot.getPath()));

      initProject(myWcRoot, this.getTestName());
      activateVCS(SvnVcs.VCS_NAME);

      myGate = new MockChangeListManagerGate(ChangeListManager.getInstance(myProject));

      ((StartupManagerImpl)StartupManager.getInstance(myProject)).runPostStartupActivities();
      refreshSvnMappingsSynchronously();
    });

    // there should be kind-a waiting for after change list manager finds all changes and runs inner refresh of copies in the above method
    if (myInitChangeListManager) {
      ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
      changeListManager.ensureUpToDate(false);
    }
  }

  protected void refreshSvnMappingsSynchronously() {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    if (! myInitChangeListManager) {
      return;
    }
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    ((SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping()).realRefresh(() -> semaphore.up());
    semaphore.waitFor();
  }

  @Override
  protected void projectCreated() {
    SvnApplicationSettings.getInstance().setCommandLinePath(myClientBinaryPath + File.separator + "svn");
  }

  @After
  public void tearDown() throws Exception {
    ((ChangeListManagerImpl) ChangeListManager.getInstance(myProject)).stopEveryThingIfInTestMode();
    sleep(100);
    runInEdtAndWait(() -> {
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
    runInAndVerifyIgnoreOutput("ci", "-m", "test");
  }

  protected void update() throws IOException {
    runInAndVerifyIgnoreOutput("up");
  }

  protected List<Change> getChangesInScope(final VcsDirtyScope dirtyScope) throws VcsException {
    ChangeProvider changeProvider = SvnVcs.getInstance(myProject).getChangeProvider();
    MockChangelistBuilder builder = new MockChangelistBuilder();
    changeProvider.getChanges(dirtyScope, builder, new EmptyProgressIndicator(), myGate);
    return builder.getChanges();
  }

  protected void undo() {
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      final TestDialog oldTestDialog = Messages.setTestDialog(TestDialog.OK);
      try {
        UndoManager.getInstance(myProject).undo(null);
      }
      finally {
        Messages.setTestDialog(oldTestDialog);
      }
    });
  }

  protected void prepareInnerCopy(final boolean anotherRepository) throws Exception {
    final String mainUrl = myRepoUrl + "/root/source";
    final String externalURL;
    if (anotherRepository) {
      createAnotherRepo();
      externalURL = myAnotherRepoUrl + "/root/target";
    } else {
      externalURL = myRepoUrl + "/root/target";
    }

    final ChangeListManagerImpl clManager = (ChangeListManagerImpl)ChangeListManager.getInstance(myProject);
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();
    clManager.stopEveryThingIfInTestMode();
    sleep(100);
    final File rootFile = virtualToIoFile(subTree.myRootDir);
    FileUtil.delete(rootFile);
    FileUtil.delete(new File(myWorkingCopyDir.getPath() + File.separator + ".svn"));
    assertTrue(!rootFile.exists());
    sleep(200);
    myWorkingCopyDir.refresh(false, true);

    runInAndVerifyIgnoreOutput("co", mainUrl);
    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    final File innerDir = new File(sourceDir, "inner1/inner2/inner");
    runInAndVerifyIgnoreOutput("co", externalURL, innerDir.getPath());
    sleep(100);
    myWorkingCopyDir.refresh(false, true);
    // above is preparation

    // start change list manager again
    clManager.forceGoInTestMode();
    refreshSvnMappingsSynchronously();
    //clManager.ensureUpToDate(false);
    //clManager.ensureUpToDate(false);
  }

  public String getTestDataDir() {
    return StringUtil.isEmpty(ourGlobalTestDataDir) ? myTestDataDir : ourGlobalTestDataDir;
  }

  public void setTestDataDir(String testDataDir) {
    myTestDataDir = testDataDir;
  }

  protected class SubTree {
    public VirtualFile myRootDir;
    public VirtualFile mySourceDir;
    public VirtualFile myTargetDir;

    public VirtualFile myS1File;
    public VirtualFile myS2File;

    public final List<VirtualFile> myTargetFiles;
    public static final String ourS1Contents = "123";
    public static final String ourS2Contents = "abc";

    private VirtualFile findOrCreateChild(final VirtualFile parent, final String name, final String content) {
      final VirtualFile result = parent.findChild(name);
      if (result != null) return result;
      if (content == null) {
        return createDirInCommand(parent, name);
      } else {
        return createFileInCommand(parent, name, content);
      }
    }

    public SubTree(final VirtualFile base) {
      myRootDir = findOrCreateChild(base, "root", null);
      mySourceDir = findOrCreateChild(myRootDir, "source", null);
      myS1File = findOrCreateChild(mySourceDir, "s1.txt", ourS1Contents);
      myS2File = findOrCreateChild(mySourceDir, "s2.txt", ourS2Contents);

      myTargetDir = findOrCreateChild(myRootDir, "target", null);
      myTargetFiles = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        myTargetFiles.add(findOrCreateChild(myTargetDir, "t" + (i + 10) + ".txt", ourS1Contents));
      }
    }
  }

  protected static void sleep(final int millis) {
    TimeoutUtil.sleep(millis);
  }

  public String prepareBranchesStructure() throws Exception {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final String mainUrl = myRepoUrl + "/trunk";
    runInAndVerifyIgnoreOutput("mkdir", "-m", "mkdir", mainUrl);
    runInAndVerifyIgnoreOutput("mkdir", "-m", "mkdir", myRepoUrl + "/branches");
    runInAndVerifyIgnoreOutput("mkdir", "-m", "mkdir", myRepoUrl + "/tags");

    final ChangeListManagerImpl clManager = (ChangeListManagerImpl)ChangeListManager.getInstance(myProject);
    clManager.stopEveryThingIfInTestMode();
    sleep(100);
    boolean deleted = false;
    for (int i = 0; i < 5; i++) {
      deleted = FileUtil.delete(new File(myWorkingCopyDir.getPath() + File.separator + ".svn"));
      if (deleted) break;
      sleep(200);
    }
    assertTrue(deleted);
    sleep(200);
    myWorkingCopyDir.refresh(false, true);

    runInAndVerifyIgnoreOutput("co", mainUrl, myWorkingCopyDir.getPath());
    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final String branchUrl = myRepoUrl + "/branches/b1";
    runInAndVerifyIgnoreOutput("copy", "-q", "-m", "coppy", mainUrl, branchUrl);

    clManager.forceGoInTestMode();
    refreshSvnMappingsSynchronously();
    //clManager.ensureUpToDate(false);
    //clManager.ensureUpToDate(false);

    return branchUrl;
  }

  public void prepareExternal() throws Exception {
    prepareExternal(true, true, false);
  }

  public void prepareExternal(final boolean commitExternalDefinition, final boolean updateExternal,
                              final boolean anotherRepository) throws Exception {
    final ChangeListManagerImpl clManager = (ChangeListManagerImpl)ChangeListManager.getInstance(myProject);
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final String mainUrl = myRepoUrl + "/root/source";
    final String externalURL;
    if (anotherRepository) {
      createAnotherRepo();
      externalURL = myAnotherRepoUrl + "/root/target";
    } else {
      externalURL = myRepoUrl + "/root/target";
    }

    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();
    clManager.stopEveryThingIfInTestMode();
    sleep(100);
    final File rootFile = virtualToIoFile(subTree.myRootDir);
    FileUtil.delete(rootFile);
    FileUtil.delete(new File(myWorkingCopyDir.getPath() + File.separator + ".svn"));
    assertTrue(!rootFile.exists());
    sleep(200);
    myWorkingCopyDir.refresh(false, true);

    final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
    runInAndVerifyIgnoreOutput("co", mainUrl, sourceDir.getPath());
    CreateExternalAction.addToExternalProperty(vcs, sourceDir, "external", externalURL);
    sleep(100);

    if (updateExternal) {
      runInAndVerifyIgnoreOutput("up", sourceDir.getPath());
    }
    if (commitExternalDefinition) {
      runInAndVerifyIgnoreOutput("ci", "-m", "test", sourceDir.getPath());
    }
    sleep(100);

    if (updateExternal) {
      myWorkingCopyDir.refresh(false, true);
      assertTrue(new File(sourceDir, "external").exists());
    }
    // above is preparation

    // start change list manager again
    clManager.forceGoInTestMode();
    refreshSvnMappingsSynchronously();
    //clManager.ensureUpToDate(false);
    //clManager.ensureUpToDate(false);
  }

  protected void createAnotherRepo() throws Exception {
    final File repo = FileUtil.createTempDirectory("anotherRepo", "");
    FileUtil.delete(repo);
    FileUtil.copyDir(myRepoRoot, repo);
    myAnotherRepoUrl = (SystemInfo.isWindows ? "file:///" : "file://") + FileUtil.toSystemIndependentName(repo.getPath());
    final File tmpWc = FileUtil.createTempDirectory("hhh", "");
    runInAndVerifyIgnoreOutput("co", myAnotherRepoUrl, tmpWc.getPath());
    final VirtualFile tmpWcVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tmpWc);
    assertNotNull(tmpWcVf);
    final SubTree tree = new SubTree(tmpWcVf);
    runInAndVerifyIgnoreOutput(tmpWc, "add", "root");
    runInAndVerifyIgnoreOutput(tmpWc, "ci", "-m", "fff");
    FileUtil.delete(tmpWc);
  }

  protected static void imitUpdate(final Project project) {
    ProjectLevelVcsManagerEx.getInstanceEx(project).getOptions(VcsConfiguration.StandardOption.UPDATE).setValue(false);
    final CommonUpdateProjectAction action = new CommonUpdateProjectAction();
    action.getTemplatePresentation().setText("1");
    action.actionPerformed(new AnActionEvent(null,
                                             dataId -> {
                                               if (CommonDataKeys.PROJECT.is(dataId)) {
                                                 return project;
                                               }
                                               return null;
                                             }, "test", new Presentation(), ActionManager.getInstance(), 0));

    final ChangeListManager clManager = ChangeListManager.getInstance(project);
    clManager.ensureUpToDate(false);
    clManager.ensureUpToDate(false);  // wait for after-events like annotations recalculation
    sleep(100); // zipper updater
  }

  protected void runAndVerifyStatusSorted(final String... stdoutLines) throws IOException {
    runStatusAcrossLocks(myWcRoot, true, stdoutLines);
  }

  protected void runAndVerifyStatus(final String... stdoutLines) throws IOException {
    runStatusAcrossLocks(myWcRoot, false, stdoutLines);
  }

  private void runStatusAcrossLocks(@Nullable File workingDir, final boolean sorted, final String... stdoutLines) throws IOException {
    final Processor<ProcessOutput> primitiveVerifier = output -> {
      if (sorted) {
        verifySorted(output, stdoutLines);  // will assert if err not empty
      } else {
        verify(output, stdoutLines);  // will assert if err not empty
      }
      return false;
    };
    runAndVerifyAcrossLocks(workingDir, new String[]{"status"}, output -> {
      final List<String> lines = output.getStdoutLines();
      for (String line : lines) {
        if (line.trim().startsWith("L")) {
          return true; // i.e. continue tries
        }
      }
      primitiveVerifier.process(output);
      return false;
    }, primitiveVerifier);
  }

  protected void runInAndVerifyIgnoreOutput(final String... inLines) throws IOException {
    final Processor<ProcessOutput> verifier = createPrimitiveExitCodeVerifier();
    runAndVerifyAcrossLocks(myWcRoot, myRunner, inLines, verifier, verifier);
  }

  private static Processor<ProcessOutput> createPrimitiveExitCodeVerifier() {
    return output -> {
      assertEquals(output.getStderr(), 0, output.getExitCode());
      return false;
    };
  }

  public static void runInAndVerifyIgnoreOutput(File workingDir, final TestClientRunner runner, final String[] input, final String... stdoutLines) throws IOException {
    final Processor<ProcessOutput> verifier = createPrimitiveExitCodeVerifier();
    runAndVerifyAcrossLocks(workingDir, runner, input, verifier, verifier);
  }

  protected void runInAndVerifyIgnoreOutput(final File root, final String... inLines) throws IOException {
    final Processor<ProcessOutput> verifier = createPrimitiveExitCodeVerifier();
    runAndVerifyAcrossLocks(root, myRunner, inLines, verifier, verifier);
  }

  private void runAndVerifyAcrossLocks(@Nullable File workingDir, final String[] input, final Processor<ProcessOutput> verifier,
                                       final Processor<ProcessOutput> primitiveVerifier) throws IOException {
    workingDir = workingDir == null ? myWcRoot : workingDir;
    runAndVerifyAcrossLocks(workingDir, myRunner, input, verifier, primitiveVerifier);
  }

  /**
   * @param verifier - if returns true, try again
   */
  public static void runAndVerifyAcrossLocks(File workingDir, final TestClientRunner runner, final String[] input,
    final Processor<ProcessOutput> verifier, final Processor<ProcessOutput> primitiveVerifier) throws IOException {
    for (int i = 0; i < 5; i++) {
      final ProcessOutput output = runner.runClient("svn", null, workingDir, input);
      if (output.getExitCode() == 0) {
        if (verifier.process(output)) {
          continue;
        }
        return;
      }

      if (! StringUtil.isEmptyOrSpaces(output.getStderr())) {
        final String stderr = output.getStderr();
        /*svn: E155004: Working copy '' locked.
        svn: E155004: '' is already locked.
        svn: run 'svn cleanup' to remove locks (type 'svn help cleanup' for details)*/
        if (stderr.contains("E155004") && stderr.contains("is already locked")) {
          continue;
        }
      }
      // will throw assertion
      if (verifier.process(output)) {
        continue;
      }
      return;
    }
    final ProcessOutput output = runner.runClient("svn", null, workingDir, input);
    primitiveVerifier.process(output);
  }

  protected void setNativeAcceleration(final boolean value) {
    System.out.println("Set native acceleration to " + value);
    SvnApplicationSettings.getInstance().setCommandLinePath(myClientBinaryPath + File.separator + "svn");
  }
}
