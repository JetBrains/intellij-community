/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project.importing;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenFoldersImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;

public class MavenFoldersImporterTest extends MavenImportingTestCase {
  public void testUpdatingExternallyCreatedFolders() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myProjectRoot.getChildren(); // make sure fs is cached

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    new File(myProjectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs();
    updateProjectFolders();

    assertExcludes("project", "target");
    assertGeneratedSources("project", "target/generated-sources/xxx");
    
    assertNull(myProjectRoot.findChild("target"));
  }

  public void testIgnoreTargetFolder() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/classes").mkdirs();
    updateProjectFolders();

    assertExcludes("project", "target");
    myProjectRoot.refresh(false, true);
    VirtualFile target = myProjectRoot.findChild("target");
    assertNotNull(target);
    if (!Registry.is("ide.hide.excluded.files")) {
      assertTrue(ChangeListManager.getInstance(myProject).isIgnoredFile(target));
    }
  }

  public void testUpdatingFoldersForAllTheProjects() {
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

    new File(myProjectRoot.getPath(), "m1/target/foo/z").mkdirs();
    new File(myProjectRoot.getPath(), "m1/target/generated-sources/xxx/z").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/bar").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/generated-sources/yyy/z").mkdirs();

    updateProjectFolders();

    assertExcludes("m1", "target");
    assertGeneratedSources("m1", "target/generated-sources/xxx");

    assertExcludes("m2", "target");
    assertGeneratedSources("m2", "target/generated-sources/yyy");
  }

  public void testDoesNotTouchSourceFolders() {
    createStdProjectFolders();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");

    updateProjectFolders();

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  public void testDoesNotExcludeRegisteredSources() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    final File sourceDir = new File(myProjectRoot.getPath(), "target/src");
    sourceDir.mkdirs();

    ApplicationManager.getApplication().runWriteAction(() -> {
      MavenRootModelAdapter adapter = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                                getModule("project"),
                                                                new IdeModifiableModelsProviderImpl(myProject));
      adapter.addSourceFolder(sourceDir.getPath(), JavaSourceRootType.SOURCE);
      adapter.getRootModel().commit();
    });


    updateProjectFolders();

    assertSources("project", "target/src");
    assertExcludes("project", "target");
  }

  public void testDoesNothingWithNonMavenModules() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createModule("userModule");
    updateProjectFolders(); // shouldn't throw exceptions
  }

  public void testDoNotUpdateOutputFoldersWhenUpdatingExcludedFolders() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    ApplicationManager.getApplication().runWriteAction(() -> {
      MavenRootModelAdapter adapter = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                                getModule("project"),
                                                                new IdeModifiableModelsProviderImpl(myProject));
      adapter.useModuleOutput(new File(myProjectRoot.getPath(), "target/my-classes").getPath(),
                              new File(myProjectRoot.getPath(), "target/my-test-classes").getPath());
      adapter.getRootModel().commit();
    });


    MavenFoldersImporter.updateProjectFolders(myProject, true);

    ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule("project"));
    CompilerModuleExtension compiler = rootManager.getModuleExtension(CompilerModuleExtension.class);
    assertTrue(compiler.getCompilerOutputUrl(), compiler.getCompilerOutputUrl().endsWith("my-classes"));
    assertTrue(compiler.getCompilerOutputUrlForTests(), compiler.getCompilerOutputUrlForTests().endsWith("my-test-classes"));
  }

  public void testDoNotCommitIfFoldersWasNotChanged() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    final int[] count = new int[]{0};
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        count[0]++;
      }
    });

    updateProjectFolders();

    assertEquals(0, count[0]);
  }

  public void testCommitOnlyOnceForAllModules() {
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
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        count[0]++;
      }
    });

    new File(myProjectRoot.getPath(), "target/generated-sources/foo/z").mkdirs();
    new File(m1.getPath(), "target/generated-sources/bar/z").mkdirs();
    new File(m2.getPath(), "target/generated-sources/baz/z").mkdirs();

    updateProjectFolders();

    assertEquals(1, count[0]);
  }

  public void testMarkSourcesAsGeneratedOnReImport() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    new File(myProjectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs();
    updateProjectFolders();

    assertGeneratedSources("project", "target/generated-sources/xxx");

    ModuleRootModificationUtil.updateModel(getModule("project"), model -> {
      for (SourceFolder folder : model.getContentEntries()[0].getSourceFolders()) {
        JavaSourceRootProperties properties = folder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
        assertNotNull(properties);
        properties.setForGeneratedSources(false);
      }
    });
    assertGeneratedSources("project");

    importProject();
    assertGeneratedSources("project", "target/generated-sources/xxx");
  }

  public void testCustomPomFileNameDefaultContentRoots() throws Exception {
    createProjectSubFile("m1/customName.xml", createPomXml(
                  "<artifactId>m1</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>project</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>" +

                  "<build>" +
                  "  <sourceDirectory>sources</sourceDirectory>" +
                  "  <testSourceDirectory>tests</testSourceDirectory>" +
                  "</build>"));

    new File(myProjectRoot.getPath(), "m1/sources").mkdirs();
    new File(myProjectRoot.getPath(), "m1/tests").mkdirs();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m1/customName.xml</module>" +
                  "</modules>");

    assertContentRoots("m1", getProjectPath() + "/m1");
  }

  public void testCustomPomFileNameCustomContentRoots() throws Exception {
    createProjectSubFile("m1/pom.xml", createPomXml(
                  "<artifactId>m1-pom</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>project</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>"));

    createProjectSubFile("m1/custom.xml", createPomXml(
                  "<artifactId>m1-custom</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>test</groupId>" +
                  "  <artifactId>project</artifactId>" +
                  "  <version>1</version>" +
                  "</parent>" +

                  "<build>" +
                  "  <resources><resource><directory>sources/resources</directory></resource></resources>" +
                  "  <sourceDirectory>sources</sourceDirectory>" +
                  "  <testSourceDirectory>tests</testSourceDirectory>" +
                  "</build>"));

    new File(myProjectRoot.getPath(), "m1/sources/resources").mkdirs();
    new File(myProjectRoot.getPath(), "m1/tests").mkdirs();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<packaging>pom</packaging>" +
                  "<version>1</version>" +

                  "<modules>" +
                  "  <module>m1</module>" +
                  "  <module>m1/custom.xml</module>" +
                  "</modules>");

    assertModules("project", "m1-pom", "m1-custom");

    assertContentRoots("m1-pom", getProjectPath() + "/m1");
    assertContentRoots("m1-custom", getProjectPath() + "/m1/sources", getProjectPath() + "/m1/tests");
  }

  private void updateProjectFolders() {
    MavenFoldersImporter.updateProjectFolders(myProject, false);
  }
}
