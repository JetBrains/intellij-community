/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenFoldersImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;

import java.io.File;

public class MavenFoldersImporterTest extends MavenImportingTestCase {
  public void testUpdatingExternallyCreatedFolders() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myProjectRoot.getChildren(); // make sure fs is cached

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    new File(myProjectRoot.getPath(), "target/generated-sources/xxx").mkdirs();
    updateProjectFolders();

    assertExcludes("project", "target/foo");
    assertSources("project", "target/generated-sources/xxx");
    
    assertNull(myProjectRoot.findChild("target"));
  }

  public void testUpdatingFoldersForAllTheProjects() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    importProject();

    assertExcludes("m1", "target");
    assertExcludes("m2", "target");

    new File(myProjectRoot.getPath(), "m1/target/foo").mkdirs();
    new File(myProjectRoot.getPath(), "m1/target/generated-sources/xxx").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/bar").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/generated-sources/yyy").mkdirs();

    updateProjectFolders();

    assertExcludes("m1", "target/foo");
    assertSources("m1", "target/generated-sources/xxx");

    assertExcludes("m2", "target/bar");
    assertSources("m2", "target/generated-sources/yyy");
  }

  public void testDoesNotTouchSourceFolders() throws Exception {
    createStdProjectFolders();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java", "src/main/resources");
    assertTestSources("project", "src/test/java", "src/test/resources");

    updateProjectFolders();

    assertSources("project", "src/main/java", "src/main/resources");
    assertTestSources("project", "src/test/java", "src/test/resources");
  }

  public void testDoesNotExcludeRegisteredSources() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    File sourceDir = new File(myProjectRoot.getPath(), "target/src");
    sourceDir.mkdirs();

    MavenRootModelAdapter adapter = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                              getModule("project"),
                                                              new MavenDefaultModifiableModelsProvider(myProject));
    adapter.addSourceFolder(sourceDir.getPath(), false);
    adapter.getRootModel().commit();

    updateProjectFolders();

    assertSources("project", "target/src");
    assertExcludes("project", "target/foo");
  }

  public void testDoesNothingWithNonMavenModules() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createModule("userModule");
    updateProjectFolders(); // shouldn't throw exceptions
  }

  public void testDoNotUpdateOutputFoldersWhenUpdatingExcludedFolders() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    MavenRootModelAdapter adapter = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                              getModule("project"),
                                                              new MavenDefaultModifiableModelsProvider(myProject));
    adapter.useModuleOutput(new File(myProjectRoot.getPath(), "target/my-classes").getPath(),
                            new File(myProjectRoot.getPath(), "target/my-test-classes").getPath());
    adapter.getRootModel().commit();

    MavenFoldersImporter.updateProjectFolders(myProject, true);

    ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule("project"));
    CompilerModuleExtension compiler = rootManager.getModuleExtension(CompilerModuleExtension.class);
    assertTrue(compiler.getCompilerOutputUrl(), compiler.getCompilerOutputUrl().endsWith("my-classes"));
    assertTrue(compiler.getCompilerOutputUrlForTests(), compiler.getCompilerOutputUrlForTests().endsWith("my-test-classes"));
  }

  public void testDoNotCommitIfFoldersWasNotChanged() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    final int[] count = new int[]{0};
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        count[0]++;
      }
    });

    updateProjectFolders();

    assertEquals(0, count[0]);
  }

  public void testCommitOnlyOnceForAllModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    importProject();

    final int[] count = new int[]{0};
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        count[0]++;
      }
    });

    new File(myProjectRoot.getPath(), "target/generated-sources/foo").mkdirs();
    new File(m1.getPath(), "target/generated-sources/bar").mkdirs();
    new File(m2.getPath(), "target/generated-sources/baz").mkdirs();

    updateProjectFolders();

    assertEquals(1, count[0]);
  }

  private void updateProjectFolders() {
    MavenFoldersImporter.updateProjectFolders(myProject, false);
  }
}
