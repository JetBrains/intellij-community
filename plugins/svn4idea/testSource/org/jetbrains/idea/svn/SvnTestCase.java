package org.jetbrains.idea.svn;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public abstract class SvnTestCase extends AbstractVcsTestCase {
  private TempDirTestFixture myTempDirFixture;
  private File myWcRoot;

  @Before
  public void setUp() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    myTempDirFixture = fixtureFactory.createTempDirTestFixture();

    final File svnRoot = new File(myTempDirFixture.getTempDirPath(), "svnroot");
    svnRoot.mkdir();

    File pluginRoot = new File(PathManager.getHomePath(), "svnPlugins/svn4idea");
    if (!pluginRoot.isDirectory()) {
      // try standalone mode
      Class aClass = SvnTestCase.class;
      String rootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
      pluginRoot = new File(rootPath).getParentFile().getParentFile().getParentFile();
    }
    myClientBinaryPath = new File(pluginRoot, "testData/svn/bin");

    verify(runSvnAdmin("create", svnRoot.getPath()));

    myWcRoot = new File(myTempDirFixture.getTempDirPath(), "wcroot");
    myWcRoot.mkdir();

    final String url = "file:///" + FileUtil.toSystemIndependentName(svnRoot.getPath());
    verify(runSvn("co", url, "."));

    initProject(myWcRoot);
    activateVCS(SvnVcs.VCS_NAME);
  }

  @After
  public void tearDown() throws Exception {
    tearDownProject();
    if (myTempDirFixture != null) {
      myTempDirFixture.tearDown();
      myTempDirFixture = null;
    }
  }

  protected RunResult runSvnAdmin(String... commandLine) throws IOException {
    return runClient("svnadmin", null, null, commandLine);
  }

  protected RunResult runSvn(String... commandLine) throws IOException {
    return runClient("svn", null, myWcRoot, commandLine);
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

  protected List<Change> getAllChanges() throws VcsException {
    return getChangesInScope(getAllDirtyScope());
  }

  protected List<Change> getChangesForFile(VirtualFile file) throws VcsException {
    return getChangesInScope(getDirtyScopeForFile(file));
  }

  private List<Change> getChangesInScope(final VcsDirtyScope dirtyScope) throws VcsException {
    ChangeProvider changeProvider = SvnVcs.getInstance(myProject).getChangeProvider();
    assert changeProvider != null;
    MockChangelistBuilder builder = new MockChangelistBuilder();
    changeProvider.getChanges(dirtyScope, builder, new EmptyProgressIndicator());
    return builder.getChanges();
  }
}
