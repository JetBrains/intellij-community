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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;

@SuppressWarnings({"ConstantConditions"})
public class MavenExecutionTest extends MavenImportingTestCase {

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  public void testExternalExecutor() throws Exception {
    if (!hasMavenInstallation()) return;

    VfsUtil.saveText(createProjectSubFile("src/main/java/A.java"), "public class A {}");
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> objectResult) throws Throwable {
        createProjectPom("<groupId>test</groupId>" +
                         "<artifactId>project</artifactId>" +
                         "<version>1</version>");
      }
    }.execute();

    assertFalse(new File(getProjectPath(), "target").exists());

    execute(new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), Collections.emptyList()));

    assertTrue(new File(getProjectPath(), "target").exists());
  }

  public void testUpdatingExcludedFoldersAfterExecution() throws Exception {
    if (!hasMavenInstallation()) return;

    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> objectResult) throws Throwable {
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

    MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), Collections.emptyList());
    execute(params);

    SwingUtilities.invokeAndWait(() -> {
    });

    assertSources("project",
                  "src/main/java",
                  "target/generated-sources/foo");
    assertResources("project",
                    "src/main/resources");

    assertExcludes("project",
                   "target/bar",
                   "target/classes",
                   "target/classes"); // output dirs are collected twice for exclusion and for compiler output
  }

  private void execute(final MavenRunnerParameters params) {
    final Semaphore sema = new Semaphore();
    sema.down();
    UIUtil.invokeLaterIfNeeded(() -> MavenRunConfigurationType.runConfiguration(
      myProject, params, getMavenGeneralSettings(),
      new MavenRunnerSettings(),
      new ProgramRunner.Callback() {
        @Override
        public void processStarted(final RunContentDescriptor descriptor) {
          descriptor.getProcessHandler().addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
              sema.up();
              UIUtil.invokeLaterIfNeeded(() -> Disposer.dispose(descriptor));
            }
          });
        }
      }));
    sema.waitFor();
  }
}
