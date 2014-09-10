package org.jetbrains.plugins.settingsRepository;

import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtilRt;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.settingsRepository.git.AddFile;
import org.jetbrains.plugins.settingsRepository.git.GitPackage;
import org.jetbrains.plugins.settingsRepository.git.GitRepositoryManager;
import org.junit.*;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

@SuppressWarnings("JUnitTestClassNamingConvention")
public class GitTest {
  private static File ICS_DIR;

  private IdeaProjectTestFixture fixture;

  private File remoteRepository;

  @Rule
  public final GitTestWatcher testWatcher = new GitTestWatcher();

  static {
    Logger.setFactory(TestLoggerFactory.class);
    PlatformTestCase.initPlatformLangPrefix();
  }

  private Git remoteRepositoryApi;

  @BeforeClass
  public static void setIcsDir() throws IOException {
    String icsDirPath = System.getProperty("ics.settingsRepository");
    if (icsDirPath == null) {
      ICS_DIR = FileUtil.generateRandomTemporaryPath();
      System.setProperty("ics.settingsRepository", ICS_DIR.getAbsolutePath());
    }
    else {
      ICS_DIR = new File(FileUtil.expandUserHome(icsDirPath));
      FileUtil.delete(ICS_DIR);
    }
  }

  @Before
  public void setUp() throws Exception {
    fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder().getFixture();
    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          fixture.setUp();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    IcsManager icsManager = IcsManager.OBJECT$.getInstance();
    ((GitRepositoryManager)icsManager.getRepositoryManager()).recreateRepository();
    icsManager.setRepositoryActive(true);
  }

  @After
  public void tearDown() throws Exception {
    remoteRepository = null;
    remoteRepositoryApi = null;

    IcsManager.OBJECT$.getInstance().setRepositoryActive(false);
    try {
      if (fixture != null) {
        SwingUtilities.invokeAndWait(new Runnable() {
          @Override
          public void run() {
            try {
              fixture.tearDown();
            }
            catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
    }
    finally {
      if (ICS_DIR != null) {
        FileUtil.delete(ICS_DIR);
      }
    }
  }

  @NotNull
  private static String getTestDataPath() {
    return PathManager.getHomePath() + "/settings-repository/testData";
  }

  @NotNull
  private static GitRepositoryManager getRepositoryManager() {
    return ((GitRepositoryManager)IcsManager.OBJECT$.getInstance().getRepositoryManager());
  }

  @Test
  public void add() throws IOException {
    byte[] data = FileUtil.loadFileBytes(new File(getTestDataPath(), "remote.xml"));
    String addedFile = "$APP_CONFIG$/remote.xml";
    getProvider().saveContent(addedFile, data, data.length, RoamingType.PER_USER, false);

    IndexDiff diff = GitPackage.computeIndexDiff(getRepositoryManager().getRepository());
    assertThat(diff.diff(), equalTo(true));
    assertThat(diff.getAdded(), contains(equalTo(addedFile)));
    assertThat(diff.getChanged(), empty());
    assertThat(diff.getRemoved(), empty());
    assertThat(diff.getModified(), empty());
    assertThat(diff.getUntracked(), empty());
    assertThat(diff.getUntrackedFolders(), empty());
  }

  @Test
  public void addSeveral() throws IOException {
    byte[] data = FileUtil.loadFileBytes(new File(getTestDataPath(), "remote.xml"));
    byte[] data2 = FileUtil.loadFileBytes(new File(getTestDataPath(), "local.xml"));
    String addedFile = "$APP_CONFIG$/remote.xml";
    String addedFile2 = "$APP_CONFIG$/local.xml";
    getProvider().saveContent(addedFile, data, data.length, RoamingType.PER_USER, false);
    getProvider().saveContent(addedFile2, data2, data2.length, RoamingType.PER_USER, false);

    IndexDiff diff = GitPackage.computeIndexDiff(getRepositoryManager().getRepository());
    assertThat(diff.diff(), equalTo(true));
    assertThat(diff.getAdded(), contains(equalTo(addedFile), equalTo(addedFile2)));
    assertThat(diff.getChanged(), empty());
    assertThat(diff.getRemoved(), empty());
    assertThat(diff.getModified(), empty());
    assertThat(diff.getUntracked(), empty());
    assertThat(diff.getUntrackedFolders(), empty());
  }

  @Test
  public void delete() throws IOException {
    byte[] data = FileUtil.loadFileBytes(new File(getTestDataPath(), "remote.xml"));
    delete(data, false);
    delete(data, true);
  }

  private static void delete(byte[] data, boolean directory) throws IOException {
    String addedFile = "$APP_CONFIG$/remote.xml";
    getProvider().saveContent(addedFile, data, data.length, RoamingType.PER_USER, true);
    getProvider().delete(directory ? "$APP_CONFIG$" : addedFile, RoamingType.PER_USER);

    IndexDiff diff = GitPackage.computeIndexDiff(getRepositoryManager().getRepository());
    assertThat(diff.diff(), equalTo(false));
    assertThat(diff.getAdded(), empty());
    assertThat(diff.getChanged(), empty());
    assertThat(diff.getRemoved(), empty());
    assertThat(diff.getModified(), empty());
    assertThat(diff.getUntracked(), empty());
    assertThat(diff.getUntrackedFolders(), empty());
  }

  @NotNull
  private static StreamProvider getProvider() {
    StreamProvider provider = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager().getStreamProvider();
    assertThat(provider, notNullValue());
    return provider;
  }

  @Test
  public void setUpstream() throws Exception {
    String url = "https://github.com/user/repo.git";
    getRepositoryManager().setUpstream(url, null);
    assertThat(getRepositoryManager().getUpstream(), equalTo(url));
  }

  @Test
  public void pullToRepositoryWithoutCommits() throws Exception {
    doPullToRepositoryWithoutCommits(null);
  }

  @Test
  public void pullToRepositoryWithoutCommitsAndCustomRemoteBranchName() throws Exception {
    doPullToRepositoryWithoutCommits("customRemoteBranchName");
  }

  private void doPullToRepositoryWithoutCommits(@Nullable String remoteBranchName) throws Exception {
    BaseRepositoryManager repositoryManager = getRepositoryManager();
    File remoteRepository = createFileRemote(remoteBranchName);
    repositoryManager.setUpstream(remoteRepository.getAbsolutePath(), remoteBranchName);
    repositoryManager.pull(new EmptyProgressIndicator());
    compareFiles(getRepository().getWorkTree(), remoteRepository);
  }

  @Test
  public void pullToRepositoryWithCommits() throws Exception {
    doPullToRepositoryWithCommits(null);
  }

  @Test
  public void pullToRepositoryWithCommitsAndCustomRemoteBranchName() throws Exception {
    doPullToRepositoryWithCommits("customRemoteBranchName");
  }

  private void doPullToRepositoryWithCommits(@Nullable String remoteBranchName) throws Exception {
    Pair<String, byte[]> file = createLocalRepositoryAndCommit(remoteBranchName);

    BaseRepositoryManager repositoryManager = getRepositoryManager();
    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    repositoryManager.commit(progressIndicator);
    repositoryManager.pull(progressIndicator);
    assertThat(FileUtil.loadFile(new File(getRepository().getWorkTree(), file.first)), equalTo(new String(file.second, CharsetToolkit.UTF8_CHARSET)));
    compareFiles(getRepository().getWorkTree(), remoteRepository, PathUtilRt.getFileName(file.first));
  }

  @NotNull
  private Pair<String, byte[]> createLocalRepositoryAndCommit(@Nullable String remoteBranchName) throws Exception {
    BaseRepositoryManager repositoryManager = getRepositoryManager();
    remoteRepository = createFileRemote(remoteBranchName);
    repositoryManager.setUpstream(remoteRepository.getAbsolutePath(), remoteBranchName);

    return addAndCommit("$APP_CONFIG$/local.xml");
  }

  private static Pair<String, byte[]> addAndCommit(@NotNull String path) throws Exception {
    byte[] data = FileUtil.loadFileBytes(new File(getTestDataPath(), PathUtilRt.getFileName(path)));
    getProvider().saveContent(path, data, data.length, RoamingType.PER_USER, false);
    getRepositoryManager().commit(new EmptyProgressIndicator());
    return Pair.create(path, data);
  }

  // never was merged. we reset using "merge with strategy "theirs", so, we must test - what's happen if it is not first merge? - see next test
  @Test
  public void resetToTheirsIfFirstMerge() throws Exception {
    createLocalRepositoryAndCommit(null);
    sync(SyncType.RESET_TO_THEIRS);
    MockVirtualFileSystem fs = new MockVirtualFileSystem();
    fs.findFileByPath("$APP_CONFIG$/remote.xml");
    compareFiles(getRepository().getWorkTree(), remoteRepository, fs.findFileByPath(""));
  }

  @Test
  public void resetToTheirsISecondMergeIsEmpty() throws Exception {
    createLocalRepositoryAndCommit(null);
    sync(SyncType.MERGE);

    /** we must not push to non-bare repository - but we do it in test (our sync merge equals to "pull&push"),
     "
     By default, updating the current branch in a non-bare repository
     is denied, because it will make the index and work tree inconsistent
     with what you pushed, and will require 'git reset --hard' to match the work tree to HEAD.
     "
     so, we do "git reset --hard"
     */
    remoteRepositoryApi.reset().setMode(ResetCommand.ResetType.HARD).call();

    MockVirtualFileSystem fs = new MockVirtualFileSystem();
    fs.findFileByPath("$APP_CONFIG$/local.xml");
    fs.findFileByPath("$APP_CONFIG$/remote.xml");
    compareFiles(getRepository().getWorkTree(), remoteRepository, fs.findFileByPath(""));

    addAndCommit("_mac/local2.xml");
    sync(SyncType.RESET_TO_THEIRS);

    compareFiles(getRepository().getWorkTree(), remoteRepository, fs.findFileByPath(""));
  }

  private void sync(@NotNull final SyncType syncType) throws InterruptedException, InvocationTargetException {
    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          IcsManager.OBJECT$.getInstance().sync(syncType, fixture.getProject());
        }
        catch (Exception e) {
          throw new AssertionError(e);
        }
      }
    });
  }

  private static void compareFiles(@NotNull File local, @NotNull File remote, String... localExcludes) throws IOException {
    compareFiles(local, remote, null, localExcludes);
  }

  private static void compareFiles(@NotNull File local, @NotNull File remote, @Nullable VirtualFile expected, String... localExcludes) throws IOException {
    String[] localFiles = local.list();
    String[] remoteFiles = remote.list();

    assertThat(localFiles, notNullValue());
    assertThat(remoteFiles, notNullValue());

    localFiles = ArrayUtil.remove(localFiles, Constants.DOT_GIT);
    remoteFiles = ArrayUtil.remove(remoteFiles, Constants.DOT_GIT);

    Arrays.sort(localFiles);
    Arrays.sort(remoteFiles);

    if (localExcludes.length != 0) {
      for (String localExclude : localExcludes) {
        localFiles = ArrayUtil.remove(localFiles, localExclude);
      }
    }

    assertThat(localFiles, equalTo(remoteFiles));

    VirtualFile[] expectedFiles;
    if (expected == null) {
      expectedFiles = null;
    }
    else {
      assertThat(expected.isDirectory(), equalTo(true));
      //noinspection UnsafeVfsRecursion
      expectedFiles = expected.getChildren();
      Arrays.sort(expectedFiles, new Comparator<VirtualFile>() {
        @Override
        public int compare(@NotNull VirtualFile o1, @NotNull VirtualFile o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });

      for (int i = 0, n = expectedFiles.length; i < n; i++) {
        assertThat(localFiles[i], equalTo(expectedFiles[i].getName()));
      }
    }

    for (int i = 0, n = localFiles.length; i < n; i++) {
      File localFile = new File(local, localFiles[i]);
      File remoteFile = new File(remote, remoteFiles[i]);
      VirtualFile expectedFile;
      if (expectedFiles == null) {
        expectedFile = null;
      }
      else {
        expectedFile = expectedFiles[i];
        assertThat(expectedFile.isDirectory(), equalTo(localFile.isDirectory()));
      }

      if (localFile.isFile()) {
        assertThat(FileUtil.loadFile(localFile), equalTo(FileUtil.loadFile(remoteFile)));
      }
      else {
        compareFiles(localFile, remoteFile, expectedFile, localExcludes);
      }
    }
  }

  @NotNull
  private static Repository getRepository() {
    return getRepositoryManager().getRepository();
  }

  @NotNull
  private File createFileRemote(@Nullable String branchName) throws IOException, GitAPIException {
    Repository repository = testWatcher.getRepository(ICS_DIR);
    remoteRepositoryApi = new Git(repository);

    if (branchName != null) {
      // jgit cannot checkout&create branch if no HEAD (no commits in our empty repository), so we create initial empty commit
      remoteRepositoryApi.commit().setMessage("").call();

      remoteRepositoryApi.checkout().setCreateBranch(true).setName(branchName).call();
    }

    String addedFile = "$APP_CONFIG$/remote.xml";
    File workTree = repository.getWorkTree();
    FileUtil.copy(new File(getTestDataPath(), "remote.xml"), new File(workTree, addedFile));
    GitPackage.edit(repository, new AddFile(addedFile));
    remoteRepositoryApi.commit().setMessage("").call();
    return workTree;
  }
}
