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
import com.intellij.testFramework.AbstractJunitVcsTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.vcs.MockChangelistBuilder;
import com.intellij.util.io.ZipUtil;
import com.intellij.util.ui.UIUtil;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public abstract class SvnTestCase extends AbstractJunitVcsTestCase  {
  protected TempDirTestFixture myTempDirFixture;
  private File myRepoRoot;
  private File myWcRoot;
  protected String myRepoUrl;
  private ChangeListManagerGate myGate;
  protected AtomicSectionsAware myRefreshCopiesStub;

  protected SvnTestCase() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @Before
  public void setUp() throws Exception {
    //System.setProperty("svnkit.wc.17", "false");
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
          myClientBinaryPath = new File(pluginRoot, "testData/svn/bin");

          ZipUtil.extract(new File(pluginRoot, "testData/svn/newrepo.zip"), myRepoRoot, null);

          myWcRoot = new File(myTempDirFixture.getTempDirPath(), "wcroot");
          assert myWcRoot.mkdir() || myWcRoot.isDirectory() : myWcRoot;

          myRepoUrl = "file:///" + FileUtil.toSystemIndependentName(myRepoRoot.getPath());

          initProject(myWcRoot, SvnTestCase.this.getTestName());
          activateVCS(SvnVcs.VCS_NAME);

          verify(runSvn("co", myRepoUrl, "."));

          myGate = new MockChangeListManagerGate(ChangeListManager.getInstance(myProject));

          myRefreshCopiesStub = new AtomicSectionsAware() {
            @Override public void enter() { }
            @Override public void exit() { }
            @Override public boolean shouldExitAsap() { return false; }
            @Override public void checkShouldExit() throws ProcessCanceledException { }
          };

          final SvnVcs vcs = SvnVcs.getInstance(myProject);
          ((StartupManagerImpl) StartupManager.getInstance(myProject)).runPostStartupActivities();
          //vcs.postStartup();
          ((SvnFileUrlMappingImpl) vcs.getSvnFileUrlMapping()).realRefresh();

        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    // there should be kind-a waiting for after change list manager finds all changes and runs inner refresh of copies in the above method
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);
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
}
