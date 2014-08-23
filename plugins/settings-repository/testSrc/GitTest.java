package org.jetbrains.plugins.settingsRepository;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.impl.stores.StreamProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ArrayUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.settingsRepository.git.GitEx;
import org.jetbrains.plugins.settingsRepository.git.GitRepositoryManager;
import org.junit.*;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

@SuppressWarnings("JUnitTestClassNamingConvention")
public class GitTest {
  private static File ICS_DIR;

  private IdeaProjectTestFixture fixture;

  @Rule
  public final GitTestWatcher testWatcher = new GitTestWatcher();

  static {
    Logger.setFactory(TestLoggerFactory.class);
    PlatformTestCase.initPlatformLangPrefix();
  }

  @BeforeClass
  public static void setIcsDir() throws IOException {
    String icsDirPath = System.getProperty("ics.settingsRepository");
    if (icsDirPath == null) {
      ICS_DIR = FileUtil.createTempDirectory("ics", null);
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

    IcsManager.getInstance().setRepositoryManager(new GitRepositoryManager());
  }

  @After
  public void tearDown() throws Exception {
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
    return ((GitRepositoryManager)IcsManager.getInstance().getRepositoryManager());
  }

  @Test
  public void add() throws IOException {
    byte[] data = FileUtil.loadFileBytes(new File(getTestDataPath(), "encoding.xml"));
    String addedFile = "$APP_CONFIG$/encoding.xml";
    getProvider().saveContent(addedFile, data, data.length, RoamingType.GLOBAL, false);

    GitEx git = getRepositoryManager().getGit();
    IndexDiff diff = git.computeIndexDiff();
    assertThat(diff.diff(), equalTo(true));
    assertThat(diff.getAdded(), contains(equalTo(addedFile)));
    assertThat(diff.getChanged(), empty());
    assertThat(diff.getRemoved(), empty());
    assertThat(diff.getModified(), empty());
    assertThat(diff.getUntracked(), empty());
    assertThat(diff.getUntrackedFolders(), empty());
  }

  @Test
  public void delete() throws IOException {
    byte[] data = FileUtil.loadFileBytes(new File(getTestDataPath(), "encoding.xml"));
    delete(data, false);
    delete(data, true);
  }

  private static void delete(byte[] data, boolean directory) throws IOException {
    String addedFile = "$APP_CONFIG$/encoding.xml";
    getProvider().saveContent(addedFile, data, data.length, RoamingType.GLOBAL, true);
    getProvider().delete(directory ? "$APP_CONFIG$" : addedFile, RoamingType.GLOBAL);

    GitEx git = getRepositoryManager().getGit();
    IndexDiff diff = git.computeIndexDiff();
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
    assertThat(getRepositoryManager().getRemoteRepositoryUrl(), equalTo(url));
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
    BaseRepositoryManager repositoryManager = getRepositoryManager();
    File remoteRepository = createFileRemote(remoteBranchName);
    repositoryManager.setUpstream(remoteRepository.getAbsolutePath(), remoteBranchName);

    byte[] data = FileUtil.loadFileBytes(new File(getTestDataPath(), "crucibleConnector.xml"));
    String addedFile = "$APP_CONFIG$/crucibleConnector.xml";
    getProvider().saveContent(addedFile, data, data.length, RoamingType.GLOBAL, false);

    EmptyProgressIndicator progressIndicator = new EmptyProgressIndicator();
    repositoryManager.commit(progressIndicator);
    repositoryManager.pull(progressIndicator);
    assertThat(FileUtil.loadFile(new File(getRepository().getWorkTree(), addedFile)), equalTo(new String(data, CharsetToolkit.UTF8_CHARSET)));
    compareFiles(getRepository().getWorkTree(), remoteRepository, "crucibleConnector.xml");
  }

  private static void compareFiles(@NotNull File local, @NotNull File remote, String... localExcludes) throws IOException {
    String[] localFiles = local.list();
    String[] remoteFiles = remote.list();

    assert localFiles != null;
    assert remoteFiles != null;

    Arrays.sort(localFiles);
    Arrays.sort(remoteFiles);

    if (localExcludes.length != 0) {
      for (String localExclude : localExcludes) {
        localFiles = ArrayUtil.remove(localFiles, localExclude);
      }
    }

    assertThat(localFiles, equalTo(remoteFiles));
    for (int i = 0, n = localFiles.length; i < n; i++) {
      if (localFiles[i].equals(Constants.DOT_GIT)) {
        continue;
      }

      File localFile = new File(local, localFiles[i]);
      File remoteFile = new File(remote, remoteFiles[i]);
      if (localFile.isFile()) {
        assertThat(FileUtil.loadFile(localFile), equalTo(FileUtil.loadFile(remoteFile)));
      }
      else {
        compareFiles(localFile, remoteFile, localExcludes);
      }
    }
  }

  @NotNull
  private static Repository getRepository() {
    return getRepositoryManager().getGit().getRepository();
  }

  @NotNull
  private File createFileRemote(@Nullable String branchName) throws IOException, GitAPIException {
    GitEx git = new GitEx(testWatcher.getRepository(ICS_DIR));

    if (branchName != null) {
      // jgit cannot checkout&create branch if no HEAD (no commits in our empty repository), so we create initial empty commit
      git.commit().setMessage("").call();

      git.checkout().setCreateBranch(true).setName(branchName).call();
    }

    String addedFile = "$APP_CONFIG$/encoding.xml";
    File workTree = git.getRepository().getWorkTree();
    FileUtil.copy(new File(getTestDataPath(), "encoding.xml"), new File(workTree, addedFile));
    git.add(addedFile);
    git.commit().setMessage("").call();
    return workTree;
  }
}
