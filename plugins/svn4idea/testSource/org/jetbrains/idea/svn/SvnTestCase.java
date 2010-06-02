package org.jetbrains.idea.svn;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.lifecycle.AtomicSectionsAware;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.pending.MockChangeListManagerGate;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.util.io.ZipUtil;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public abstract class SvnTestCase extends AbstractVcsTestCase {
  protected TempDirTestFixture myTempDirFixture;
  private File myWcRoot;
  protected String myRepoUrl;
  private ChangeListManagerGate myGate;
  protected AtomicSectionsAware myRefreshCopiesStub;

  protected SvnTestCase() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @Before
  public void setUp() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    myTempDirFixture = fixtureFactory.createTempDirTestFixture();
    myTempDirFixture.setUp();

    final File svnRoot = new File(myTempDirFixture.getTempDirPath(), "svnroot");
    svnRoot.mkdir();

    File pluginRoot = new File(PluginPathManager.getPluginHomePath("svn4idea"));
    if (!pluginRoot.isDirectory()) {
      // try standalone mode
      Class aClass = SvnTestCase.class;
      String rootPath = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
      pluginRoot = new File(rootPath).getParentFile().getParentFile().getParentFile();
    }
    myClientBinaryPath = new File(pluginRoot, "testData/svn/bin");

    ZipUtil.extract(new File(pluginRoot, "testData/svn/newrepo.zip"), svnRoot, null);

    myWcRoot = new File(myTempDirFixture.getTempDirPath(), "wcroot");
    myWcRoot.mkdir();

    myRepoUrl = "file:///" + FileUtil.toSystemIndependentName(svnRoot.getPath());
    verify(runSvn("co", myRepoUrl, "."));

    initProject(myWcRoot);
    activateVCS(SvnVcs.VCS_NAME);

    myGate = new MockChangeListManagerGate(ChangeListManager.getInstance(myProject));

    myRefreshCopiesStub = new AtomicSectionsAware() {
      public void enter() {
      }
      public void exit() {
      }
      public boolean shouldExitAsap() {
        return false;
      }
      public void checkShouldExit() throws ProcessCanceledException {
      }
    };

    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    ((StartupManagerImpl) StartupManager.getInstance(myProject)).runPostStartupActivities();
    //vcs.postStartup();
    ((SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping()).realRefresh(myRefreshCopiesStub);
  }

  @After
  public void tearDown() throws Exception {
    tearDownProject();
    if (myTempDirFixture != null) {
      myTempDirFixture.tearDown();
      myTempDirFixture = null;
    }
  }

  protected ProcessOutput runSvn(String... commandLine) throws IOException {
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

  protected void update() throws IOException {
    verify(runSvn("up"));
  }

  protected List<Change> getAllChanges() throws VcsException {
    return getChangesInScope(getAllDirtyScope());
  }

  protected List<Change> getChangesForFile(VirtualFile file) throws VcsException {
    return getChangesInScope(getDirtyScopeForFile(file));
  }

  protected List<Change> getChangesInScope(final VcsDirtyScope dirtyScope) throws VcsException {
    ChangeProvider changeProvider = SvnVcs.getInstance(myProject).getChangeProvider();
    assert changeProvider != null;
    MockChangelistBuilder builder = new MockChangelistBuilder();
    changeProvider.getChanges(dirtyScope, builder, new EmptyProgressIndicator(), myGate);
    return builder.getChanges();
  }

  protected void undo() {
    final TestDialog oldTestDialog = Messages.setTestDialog(TestDialog.OK);
    try {
      UndoManager.getInstance(myProject).undo(null);
    }
    finally {
      Messages.setTestDialog(oldTestDialog);
    }
  }
}
