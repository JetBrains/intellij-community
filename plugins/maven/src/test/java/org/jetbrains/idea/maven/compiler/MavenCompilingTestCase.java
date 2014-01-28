/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.compiler;

import com.intellij.compiler.CompilerManagerImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenResourceCompilerConfigurationGenerator;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public abstract class MavenCompilingTestCase extends MavenImportingTestCase {
  protected void compileModules(final String... moduleNames) {
    compile(createModulesCompileScope(moduleNames));
  }

  protected void buildArtifacts(String... artifactNames) {
    compile(createArtifactsScope(artifactNames));
  }

  private void compile(final CompileScope scope) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        for (Module module : scope.getAffectedModules()) {
          setupJdkForModule(module.getName());
        }
        if (useJps()) {
          new MavenResourceCompilerConfigurationGenerator(myProject, MavenProjectsManager.getInstance(myProject).getProjectsTreeForTests())
            .generateBuildConfiguration(false);
        }
      }
    });

    CompilerWorkspaceConfiguration.getInstance(myProject).CLEAR_OUTPUT_DIRECTORY = true;
    CompilerManagerImpl.testSetup();

    List<VirtualFile> roots = Arrays.asList(ProjectRootManager.getInstance(myProject).getContentRoots());
    TranslatingCompilerFilesMonitor.getInstance()
      .scanSourceContent(new TranslatingCompilerFilesMonitor.ProjectRef(myProject), roots, roots.size(), true);


    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        CompilerManager.getInstance(myProject).make(scope, new CompileStatusNotification() {
          @Override
          public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
            //assertFalse(aborted);
            //assertEquals(collectMessages(compileContext, CompilerMessageCategory.ERROR), 0, errors);
            //assertEquals(collectMessages(compileContext, CompilerMessageCategory.WARNING), 0, warnings);
            semaphore.up();
          }
        });
      }
    });
    while (!semaphore.waitFor(100)) {
      if (SwingUtilities.isEventDispatchThread()) {
        UIUtil.dispatchAllInvocationEvents();
      }
    }
    if (SwingUtilities.isEventDispatchThread()) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  private CompileScope createArtifactsScope(String[] artifactNames) {
    List<Artifact> artifacts = new ArrayList<Artifact>();
    for (String name : artifactNames) {
      artifacts.add(ArtifactsTestUtil.findArtifact(myProject, name));
    }
    return ArtifactCompileScope.createArtifactsScope(myProject, artifacts);
  }

  private CompileScope createModulesCompileScope(final String[] moduleNames) {
    final List<Module> modules = new ArrayList<Module>();
    for (String name : moduleNames) {
      modules.add(getModule(name));
    }
    return new ModuleCompileScope(myProject, modules.toArray(new Module[modules.size()]), false);
  }

  protected static void assertResult(VirtualFile pomFile, String relativePath, String content) throws IOException {
    assertEquals(content, loadResult(pomFile, relativePath));
  }

  protected static String loadResult(VirtualFile pomFile, String relativePath) throws IOException {
    File file = new File(pomFile.getParent().getPath(), relativePath);
    assertTrue("file not found: " + relativePath, file.exists());
    return new String(FileUtil.loadFileText(file));
  }

  protected void assertResult(String relativePath, String content) throws IOException {
    assertResult(myProjectPom, relativePath, content);
  }

  protected void assertDirectory(String relativePath, TestFileSystemBuilder fileSystemBuilder) {
    fileSystemBuilder.build().assertDirectoryEqual(new File(myProjectPom.getParent().getPath(), relativePath));
  }
}
