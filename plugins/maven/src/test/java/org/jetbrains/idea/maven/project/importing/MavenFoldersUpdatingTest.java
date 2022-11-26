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
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.VcsIgnoreManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.*;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.junit.Test;

import java.io.File;

public class MavenFoldersUpdatingTest extends MavenMultiVersionImportingTestCase {
  @Test
  public void testUpdatingExternallyCreatedFolders() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myProjectRoot.getChildren(); // make sure fs is cached

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    new File(myProjectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs();
    updateTargetFolders();

    assertExcludes("project", "target");
    assertGeneratedSources("project", "target/generated-sources/xxx");

    assertNull(myProjectRoot.findChild("target"));
  }

  @Test 
  public void testIgnoreTargetFolder() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/classes").mkdirs();
    updateTargetFolders();

    assertExcludes("project", "target");
    myProjectRoot.refresh(false, true);
    VirtualFile target = myProjectRoot.findChild("target");
    assertNotNull(target);
    if (!Registry.is("ide.hide.excluded.files")) {
      assertTrue(VcsIgnoreManager.getInstance(myProject).isPotentiallyIgnoredFile(target));
    }
  }

  @Test 
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

    updateTargetFolders();

    assertExcludes("m1", "target");
    assertGeneratedSources("m1", "target/generated-sources/xxx");

    assertExcludes("m2", "target");
    assertGeneratedSources("m2", "target/generated-sources/yyy");
  }

  @Test 
  public void testDoesNotTouchSourceFolders() {
    createStdProjectFolders();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");

    updateTargetFolders();

    assertSources("project", "src/main/java");
    assertResources("project", "src/main/resources");
    assertTestSources("project", "src/test/java");
    assertTestResources("project", "src/test/resources");
  }

  @Test 
  public void testDoesNotExcludeRegisteredSources() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    final File sourceDir = new File(myProjectRoot.getPath(), "target/src");
    sourceDir.mkdirs();

    ApplicationManager.getApplication().runWriteAction(() -> {
      MavenRootModelAdapter adapter = new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(
        getProjectsTree().findProject(myProjectPom),
        getModule("project"),
        new ModifiableModelsProviderProxyWrapper(myProject)));
      adapter.addSourceFolder(sourceDir.getPath(), JavaSourceRootType.SOURCE);
      adapter.getRootModel().commit();
    });


    updateTargetFolders();

    if (supportsKeepingManualChanges()) {
      assertSources("project", "target/src");
    }
    else {
      assertSources("project", "src/main/java");
    }
    assertExcludes("project", "target");
  }

  @Test 
  public void testDoesNothingWithNonMavenModules() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createModule("userModule");
    updateTargetFolders(); // shouldn't throw exceptions
  }

  @Test 
  public void testDoNotUpdateOutputFoldersWhenUpdatingExcludedFolders() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    ApplicationManager.getApplication().runWriteAction(() -> {
      MavenRootModelAdapter adapter = new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(
        getProjectsTree().findProject(myProjectPom),
        getModule("project"),
        new ModifiableModelsProviderProxyWrapper(myProject)));
      adapter.useModuleOutput(new File(myProjectRoot.getPath(), "target/my-classes").getPath(),
                              new File(myProjectRoot.getPath(), "target/my-test-classes").getPath());
      adapter.getRootModel().commit();
    });


    updateTargetFolders();

    ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule("project"));
    CompilerModuleExtension compiler = rootManager.getModuleExtension(CompilerModuleExtension.class);
    assertTrue(compiler.getCompilerOutputUrl(), compiler.getCompilerOutputUrl().endsWith("my-classes"));
    assertTrue(compiler.getCompilerOutputUrlForTests(), compiler.getCompilerOutputUrlForTests().endsWith("my-test-classes"));
  }

  @Test 
  public void testDoNotCommitIfFoldersWasNotChanged() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    final int[] count = new int[]{0};
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(@NotNull ModuleRootEvent event) {
        count[0]++;
      }
    });

    updateTargetFolders();
    assertEquals(isWorkspaceImport() ? 0 : 1, count[0]);
  }

  @Test
  public void testCommitOnlyOnceForAllModules() {
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

    MavenEventsTestHelper eventsTestHelper = new MavenEventsTestHelper();
    eventsTestHelper.setUp(myProject);
    try {
      updateTargetFolders();
      eventsTestHelper.assertRootsChanged(isWorkspaceImport() ? 0 : 1);
      eventsTestHelper.assertWorkspaceModelChanges(isWorkspaceImport() ? 0 : 1);

      // let's add some generated folders, what should be picked up on updateTargetFolders
      new File(myProjectRoot.getPath(), "target/generated-sources/foo/z").mkdirs();
      new File(myProjectRoot.getPath(), "m1/target/generated-sources/bar/z").mkdirs();
      new File(myProjectRoot.getPath(), "m2/target/generated-sources/baz/z").mkdirs();
      updateTargetFolders();

      eventsTestHelper.assertRootsChanged(1);
      eventsTestHelper.assertWorkspaceModelChanges(1);
    }
    finally {
      eventsTestHelper.tearDown();
    }
  }

  @Test 
  public void testMarkSourcesAsGeneratedOnReImport() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    new File(myProjectRoot.getPath(), "target/generated-sources/xxx/z").mkdirs();
    updateTargetFolders();

    assertGeneratedSources("project", "target/generated-sources/xxx");

    ModuleRootModificationUtil.updateModel(getModule("project"), model -> {
      SourceFolder[] folders = model.getContentEntries()[0].getSourceFolders();
      SourceFolder generated = ContainerUtil.find(folders, it -> it.getUrl().endsWith("target/generated-sources/xxx"));
      assertNotNull("Generated folder not found", generated);

      JavaSourceRootProperties properties = generated.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
      assertNotNull(properties);
      properties.setForGeneratedSources(false);
    });
    assertGeneratedSources("project");

    importProject();
    assertGeneratedSources("project", "target/generated-sources/xxx");
  }

  private void updateTargetFolders() {
    MavenProjectImporter.tryUpdateTargetFolders(myProject);
  }
}
