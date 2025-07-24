// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.ScopeInfo;
import com.intellij.openapi.vcs.update.VcsUpdateProcess;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase;
import com.intellij.testFramework.vcs.MockChangeListManagerGate;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.testFramework.vcs.TestClientRunner;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.actions.CreateExternalAction;
import org.jetbrains.idea.svn.api.Url;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static com.intellij.openapi.application.PluginPathManager.getPluginHomePath;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.UsefulTestCase.*;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.map2Array;
import static com.intellij.util.lang.CompoundRuntimeException.throwIfNotEmpty;
import static java.util.Collections.singletonMap;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.junit.Assume.assumeTrue;

public abstract class SvnTestCase extends AbstractJunitVcsTestCase {
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();
  @ClassRule public static final ExternalResource ideaTempDirectoryRule = new ExternalResource() {
    @Override
    protected void before() throws Throwable {
      ensureExists(new File(PathManager.getTempPath()));
    }
  };

  private static final String ORIGINAL_TEMP_DIRECTORY = getTempDirectory();

  protected TempDirTestFixture myTempDirFixture;
  protected Url myRepositoryUrl;
  protected String myRepoUrl;
  protected TestClientRunner myRunner;
  protected String myWcRootName;

  private final String myTestDataDir;
  private File myRepoRoot;
  private File myWcRoot;
  private ChangeListManagerGate myGate;
  protected String myAnotherRepoUrl;
  protected File myPluginRoot;

  protected ProjectLevelVcsManagerImpl vcsManager;
  protected ChangeListManagerImpl changeListManager;
  protected VcsDirtyScopeManager dirtyScopeManager;
  protected SvnVcs vcs;

  protected SvnTestCase() {
    this("testData");
  }

  protected SvnTestCase(@NotNull String testDataDir) {
    myTestDataDir = testDataDir;
    myWcRootName = "wcroot";
  }

  @NotNull
  public static String getPluginHome() {
    return getPluginHomePath("svn4idea");
  }

  @BeforeClass
  public static void assumeSupportedTeamCityAgentArch() {
    if (IS_UNDER_TEAMCITY) {
      assumeTrue(SystemInfo.OS_NAME + '/' + CpuArch.CURRENT + " is not supported", !SystemInfo.isMac && CpuArch.isIntel64());
    }
  }

  @Before
  public void before() throws Exception {
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myTempDirFixture.setUp();
    resetCanonicalTempPathCache(myTempDirFixture.getTempDirPath());

    myPluginRoot = new File(getPluginHome());
    myClientBinaryPath = getSvnClientDirectory();
    myRunner =
      SystemInfo.isMac ? createClientRunner(singletonMap("DYLD_LIBRARY_PATH", myClientBinaryPath.getPath())) : createClientRunner();

    myRepoRoot = virtualToIoFile(myTempDirFixture.findOrCreateDir("svnroot"));
    ZipUtil.extract(new File(myPluginRoot, getTestDataDir() + "/svn/newrepo.zip"), myRepoRoot, null);

    myWcRoot = virtualToIoFile(myTempDirFixture.findOrCreateDir(myWcRootName));
    myRepoUrl = (SystemInfo.isWindows ? "file:///" : "file://") + toSystemIndependentName(myRepoRoot.getPath());
    myRepositoryUrl = parseUrl(myRepoUrl);

    verify(runSvn("co", myRepoUrl, myWcRoot.getPath()));

    initProject(myWcRoot, this.getTestName());
    activateVCS(SvnVcs.VCS_NAME);

    vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    vcs = SvnVcs.getInstance(myProject);
    myGate = new MockChangeListManagerGate(changeListManager);

    VfsUtil.markDirtyAndRefresh(false, true, true, myRepoRoot);
    refreshSvnMappingsSynchronously();
    refreshChanges();
  }

  @NotNull
  private File getSvnClientDirectory() {
    File svnBinDir = new File(myPluginRoot, getTestDataDir() + "/svn/bin");
    String executablePath =
      SystemInfo.isWindows ? "windows/svn.exe" : SystemInfo.isLinux ? "linux/svn" : SystemInfo.isMac ? "mac/svn" : null;
    assertNotNull("No Subversion executable was found " + SystemInfo.OS_NAME, executablePath);

    File svnExecutable = new File(svnBinDir, executablePath);
    assertTrue(svnExecutable + " is not executable", svnExecutable.canExecute());

    return svnExecutable.getParentFile();
  }

  protected void refreshSvnMappingsSynchronously() throws TimeoutException {
    vcs.getSvnFileUrlMappingImpl().scheduleRefresh();
    vcs.getSvnFileUrlMappingImpl().waitForRefresh();
  }

  protected void refreshChanges() {
    dirtyScopeManager.markEverythingDirty();
    changeListManager.ensureUpToDate();
  }

  protected void waitChangesAndAnnotations() {
    changeListManager.ensureUpToDate();
    ((VcsAnnotationLocalChangesListenerImpl)vcsManager.getAnnotationLocalChangesListener()).calmDown();
  }

  @NotNull
  protected Set<String> commit(@NotNull List<Change> changes, @NotNull String message) {
    Set<String> feedback = new HashSet<>();
    throwIfNotEmpty(vcs.getCheckinEnvironment().commit(changes, message, new CommitContext(), feedback));
    return feedback;
  }

  protected void rollback(@NotNull List<Change> changes) {
    List<VcsException> exceptions = new ArrayList<>();
    vcs.createRollbackEnvironment().rollbackChanges(changes, exceptions, RollbackProgressListener.EMPTY);
    throwIfNotEmpty(exceptions);
  }

  @Override
  protected void projectCreated() {
    SvnApplicationSettings.getInstance().setCommandLinePath(myClientBinaryPath + File.separator + "svn");
  }

  @After
  public void after() throws Exception {
    RunAll.runAll(
      this::tearDownChangeListManager,
      () -> runInEdtAndWait(this::tearDownProject),
      this::tearDownTempDirectoryFixture,
      () -> resetCanonicalTempPathCache(ORIGINAL_TEMP_DIRECTORY)
    );
  }

  private void tearDownChangeListManager() {
    if (changeListManager != null) {
      changeListManager.waitEverythingDoneInTestMode();
    }
  }

  private void tearDownTempDirectoryFixture() throws Exception {
    if (myTempDirFixture != null) {
      myTempDirFixture.tearDown();
      myTempDirFixture = null;
    }
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
    MockChangelistBuilder builder = new MockChangelistBuilder();
    vcs.getChangeProvider().getChanges(dirtyScope, builder, new EmptyProgressIndicator(), myGate);
    return builder.getChanges();
  }

  protected void undoFileMove() {
    undo("VcsTestUtil MoveFile");
  }

  protected void undoFileRename() {
    undo("VcsTestUtil RenameFile");
  }

  protected void undo() {
    undo(null);
  }

  /**
   * @param expectedCommandName Typically - command name from {@link VcsTestUtil}, be wary of ellipsis
   */
  private void undo(@Nullable String expectedCommandName) {
    runInEdtAndWait(() -> {
      final TestDialog oldTestDialog = TestDialogManager.setTestDialog(TestDialog.OK);
      try {
        UndoManager undoManager = UndoManager.getInstance(myProject);

        assertTrue("undo is not available", undoManager.isUndoAvailable(null));

        if (expectedCommandName != null) {
          String undoText = undoManager.getUndoActionNameAndDescription(null).first;
          assumeTrue("Unexpected VFS command on undo stack, suspecting IDEA-182560. Test aborted. Undo on stack: " + undoText,
                     undoText != null && undoText.contains(expectedCommandName));
        }

        undoManager.undo(null);
      }
      finally {
        TestDialogManager.setTestDialog(oldTestDialog);
      }
    });
  }

  protected void prepareInnerCopy(final boolean anotherRepository) throws Exception {
    if (anotherRepository) {
      createAnotherRepo();
    }

    final String mainUrl = myRepoUrl + "/root/source";
    final String externalURL = (anotherRepository ? myAnotherRepoUrl : myRepoUrl) + "/root/target";
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    withDisabledChangeListManager(() -> {
      final File rootFile = virtualToIoFile(subTree.myRootDir);
      delete(rootFile);
      delete(new File(myWorkingCopyDir.getPath() + File.separator + ".svn"));
      assertDoesntExist(rootFile);
      refreshVfs();

      File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
      File innerDir = new File(sourceDir, "inner1/inner2/inner");
      runInAndVerifyIgnoreOutput("co", mainUrl, sourceDir.getPath());
      runInAndVerifyIgnoreOutput("co", externalURL, innerDir.getPath());

      refreshVfs();
      setVcsMappings(createDirectoryMapping(sourceDir));
    });
  }

  public String getTestDataDir() {
    return myTestDataDir;
  }

  protected class SubTree {
    public final VirtualFile myBase;
    public VirtualFile myRootDir;
    public VirtualFile mySourceDir;
    public VirtualFile myTargetDir;

    public VirtualFile myS1File;
    public VirtualFile myS2File;

    public final List<VirtualFile> myTargetFiles = new ArrayList<>();
    public static final String ourS1Contents = "123";
    public static final String ourS2Contents = "abc";

    private VirtualFile findChild(final VirtualFile parent, final String name, final String content, boolean create) {
      final VirtualFile result = parent.findChild(name);
      if (result != null || !create) return result;
      return content == null ? createDirInCommand(parent, name) : createFileInCommand(parent, name, content);
    }

    public SubTree(@NotNull VirtualFile base) {
      myBase = base;
      refresh(true);
    }

    public void refresh(boolean create) {
      myRootDir = findChild(myBase, "root", null, create);
      mySourceDir = findChild(myRootDir, "source", null, create);
      myS1File = findChild(mySourceDir, "s1.txt", ourS1Contents, create);
      myS2File = findChild(mySourceDir, "s2.txt", ourS2Contents, create);

      myTargetDir = findChild(myRootDir, "target", null, create);
      myTargetFiles.clear();
      for (int i = 0; i < 3; i++) {
        myTargetFiles.add(findChild(myTargetDir, "t" + (i + 10) + ".txt", ourS1Contents, create));
      }
    }
  }

  public String prepareBranchesStructure() throws Exception {
    final String mainUrl = myRepoUrl + "/trunk";
    runInAndVerifyIgnoreOutput("mkdir", "-m", "mkdir", mainUrl);
    runInAndVerifyIgnoreOutput("mkdir", "-m", "mkdir", myRepoUrl + "/branches");
    runInAndVerifyIgnoreOutput("mkdir", "-m", "mkdir", myRepoUrl + "/tags");

    String branchUrl = myRepoUrl + "/branches/b1";

    withDisabledChangeListManager(() -> {
      runWithRetries(() -> {
        deleteRecursively(new File(myWorkingCopyDir.getPath() + File.separator + ".svn").toPath());
      });
      refreshVfs();

      runInAndVerifyIgnoreOutput("co", mainUrl, myWorkingCopyDir.getPath());
      enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
      new SubTree(myWorkingCopyDir);
      checkin();
      runInAndVerifyIgnoreOutput("copy", "-q", "-m", "coppy", mainUrl, branchUrl);
    });

    return branchUrl;
  }

  public void prepareExternal() throws Exception {
    prepareExternal(true, true, false);
  }

  public void prepareExternal(boolean commitExternalDefinition, boolean updateExternal, boolean anotherRepository) throws Exception {
    if (anotherRepository) {
      createAnotherRepo();
    }

    final String mainUrl = myRepoUrl + "/root/source";
    final String externalURL = (anotherRepository ? myAnotherRepoUrl : myRepoUrl) + "/root/target";
    final SubTree subTree = new SubTree(myWorkingCopyDir);
    checkin();

    withDisabledChangeListManager(() -> {
      final File rootFile = virtualToIoFile(subTree.myRootDir);
      delete(rootFile);
      delete(new File(myWorkingCopyDir.getPath() + File.separator + ".svn"));
      assertDoesntExist(rootFile);
      refreshVfs();

      final File sourceDir = new File(myWorkingCopyDir.getPath(), "source");
      runInAndVerifyIgnoreOutput("co", mainUrl, sourceDir.getPath());
      CreateExternalAction.addToExternalProperty(vcs, sourceDir, "external", externalURL);

      if (updateExternal) {
        runInAndVerifyIgnoreOutput("up", sourceDir.getPath());
      }
      if (commitExternalDefinition) {
        runInAndVerifyIgnoreOutput("ci", "-m", "test", sourceDir.getPath());
      }

      refreshVfs();
      setVcsMappings(createDirectoryMapping(sourceDir));

      if (updateExternal) {
        assertExists(new File(sourceDir, "external"));
      }
    });
  }

  private void withDisabledChangeListManager(@NotNull ThrowableRunnable<? extends Exception> action) throws Exception {
    changeListManager.waitUntilRefreshed();
    changeListManager.forceStopInTestMode();
    action.run();
    changeListManager.forceGoInTestMode();
    refreshSvnMappingsSynchronously();
  }

  @NotNull
  private VcsDirectoryMapping createDirectoryMapping(@NotNull File directory) {
    return new VcsDirectoryMapping(toSystemIndependentName(directory.getPath()), vcs.getName());
  }

  private void createAnotherRepo() throws Exception {
    File repo = virtualToIoFile(myTempDirFixture.findOrCreateDir("anotherRepo"));
    copyDir(myRepoRoot, repo);
    myAnotherRepoUrl = (SystemInfo.isWindows ? "file:///" : "file://") + toSystemIndependentName(repo.getPath());
    VirtualFile tmpWcVf = myTempDirFixture.findOrCreateDir("anotherRepoWc");
    File tmpWc = virtualToIoFile(tmpWcVf);
    runInAndVerifyIgnoreOutput("co", myAnotherRepoUrl, tmpWc.getPath());
    new SubTree(tmpWcVf);
    runInAndVerifyIgnoreOutput(tmpWc, "add", "root");
    runInAndVerifyIgnoreOutput(tmpWc, "ci", "-m", "fff");
    delete(tmpWc);
  }

  protected void imitUpdate() {
    vcsManager.getOptions(VcsConfiguration.StandardOption.UPDATE).setValue(false);
    var actionInfo = ActionInfo.UPDATE;
    var scopeInfo = ScopeInfo.PROJECT;
    var context = SimpleDataContext.getProjectContext(myProject);

    var roots = VcsUpdateProcess.getRoots(myProject, actionInfo, scopeInfo, context, true);
    var updateSpec = VcsUpdateProcess.createUpdateSpec(myProject, roots, actionInfo);
    VcsUpdateProcess.runUpdateBlocking(myProject, roots, updateSpec, actionInfo, "1");
    waitChangesAndAnnotations();
  }

  protected void runAndVerifyStatusSorted(final String... stdoutLines) throws IOException {
    runStatusAcrossLocks(myWcRoot, true, map2Array(stdoutLines, String.class, it -> toSystemDependentName(it)));
  }

  protected void runAndVerifyStatus(final String... stdoutLines) throws IOException {
    runStatusAcrossLocks(myWcRoot, false, map2Array(stdoutLines, String.class, it -> toSystemDependentName(it)));
  }

  private void runStatusAcrossLocks(@Nullable File workingDir, final boolean sorted, final String... stdoutLines) throws IOException {
    final Processor<ProcessOutput> primitiveVerifier = output -> {
      if (sorted) {
        verifySorted(output, stdoutLines);
      } else {
        verify(output, stdoutLines);
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

  public static void runInAndVerifyIgnoreOutput(File workingDir, final TestClientRunner runner, final String[] input) throws IOException {
    final Processor<ProcessOutput> verifier = createPrimitiveExitCodeVerifier();
    runAndVerifyAcrossLocks(workingDir, runner, input, verifier, verifier);
  }

  protected void runInAndVerifyIgnoreOutput(final File root, final String... inLines) throws IOException {
    final Processor<ProcessOutput> verifier = createPrimitiveExitCodeVerifier();
    runAndVerifyAcrossLocks(root, myRunner, inLines, verifier, verifier);
  }

  private void runAndVerifyAcrossLocks(@Nullable File workingDir, final String[] input, final Processor<ProcessOutput> verifier,
                                       final Processor<ProcessOutput> primitiveVerifier) throws IOException {
    workingDir = notNull(workingDir, myWcRoot);
    runAndVerifyAcrossLocks(workingDir, myRunner, input, verifier, primitiveVerifier);
  }

  private static void runAndVerifyAcrossLocks(File workingDir, final TestClientRunner runner, final String[] input,
                                              final Processor<ProcessOutput> verifier, final Processor<ProcessOutput> primitiveVerifier) throws IOException {
    for (int i = 0; i < 5; i++) {
      final ProcessOutput output = runner.runClient("svn", null, workingDir, input);
      if (output.getExitCode() != 0 && !isEmptyOrSpaces(output.getStderr())) {
        final String stderr = output.getStderr();
        if (stderr.contains("E155004") && stderr.contains("is already locked")) continue;
      }
      if (verifier.process(output)) continue;
      return;
    }
    primitiveVerifier.process(runner.runClient("svn", null, workingDir, input));
  }
}
