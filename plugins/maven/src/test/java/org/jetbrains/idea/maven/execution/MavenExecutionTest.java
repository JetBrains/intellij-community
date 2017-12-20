/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;

@SuppressWarnings({"ConstantConditions"})
public class MavenExecutionTest extends MavenImportingTestCase {

  private static final String JDK_NAME = "MavenExecutionTestJDK";
  private String myJdkHome;

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  public void setUp() throws Exception {
    edt(() -> {
      myJdkHome = IdeaTestUtil.requireRealJdkHome();
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), myJdkHome);
      super.setUp();

      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) {
          Sdk oldJdk = ProjectJdkTable.getInstance().findJdk(JDK_NAME);
          if (oldJdk != null) {
            ProjectJdkTable.getInstance().removeJdk(oldJdk);
          }
          VirtualFile jdkHomeDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(myJdkHome));
          Sdk jdk = SdkConfigurationUtil.setupSdk(new Sdk[0], jdkHomeDir, JavaSdk.getInstance(), true, null, JDK_NAME);
          assertNotNull("Cannot create JDK for " + myJdkHome, jdk);
          ProjectJdkTable.getInstance().addJdk(jdk);
          ProjectRootManager projectRootManager = ProjectRootManager.getInstance(myProject);
          if (projectRootManager.getProjectSdk() == null) {
            projectRootManager.setProjectSdk(jdk);
          }
        }
      }.execute();
    });
  }

  @Override
  public void tearDown() throws Exception {
    edt(() -> {
      if (myJdkHome == null) {
        //super.setUp() wasn't called
        return;
      }
      Sdk jdk = ProjectJdkTable.getInstance().findJdk(JDK_NAME);
      if (jdk != null) {
        new WriteAction() {
          @Override
          protected void run(@NotNull Result result) {
            ProjectJdkTable.getInstance().removeJdk(jdk);
          }
        }.execute();
      }

      super.tearDown();
    });
  }

  public void testExternalExecutor() throws Exception {
    if (!hasMavenInstallation()) return;

    edt(() -> {
      WriteAction.run(() -> {
        VfsUtil.saveText(createProjectSubFile("src/main/java/A.java"), "public class A {}");
      });
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    });

    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> objectResult) {
        createProjectPom("<groupId>test</groupId>" +
                         "<artifactId>project</artifactId>" +
                         "<version>1</version>");
      }
    }.execute();

    assertFalse(new File(getProjectPath(), "target").exists());

    execute(new MavenRunnerParameters(true, getProjectPath(), (String)null, Arrays.asList("compile"), Collections.emptyList()));

    assertTrue(new File(getProjectPath(), "target").exists());
  }

  public void testUpdatingExcludedFoldersAfterExecution() throws Exception {
    if (!hasMavenInstallation()) return;

    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> objectResult) {
        createStdProjectFolders();

        importProject("<groupId>test</groupId>" +
                      "<artifactId>project</artifactId>" +
                      "<version>1</version>");

        createProjectSubDirs("target/generated-sources/foo",
                             "target/bar");
      }
    }.execute();

    assertModules("project");
    assertExcludes("project", "target");

    MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), (String)null, Arrays.asList("compile"), Collections.emptyList());
    execute(params);

    SwingUtilities.invokeAndWait(() -> {
    });

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");

    assertExcludes("project", "target");
  }

  private void execute(final MavenRunnerParameters params) {
    final Semaphore sema = new Semaphore();
    sema.down();
    edt(() -> MavenRunConfigurationType.runConfiguration(
      myProject, params, getMavenGeneralSettings(),
      new MavenRunnerSettings(),
      new ProgramRunner.Callback() {
        @Override
        public void processStarted(final RunContentDescriptor descriptor) {
          descriptor.getProcessHandler().addProcessListener(new ProcessAdapter() {

            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
              System.out.println(event.getText());
            }

            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
              sema.up();
              edt(() -> Disposer.dispose(descriptor));
            }
          });
        }
      }));
    sema.waitFor();
  }
}
