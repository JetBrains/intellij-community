// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.maven.testFramework.MavenTestCase;
import com.intellij.maven.testFramework.utils.MavenImportingTestCaseKt;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.project.importing.MavenImportFinishedContext;
import org.jetbrains.idea.maven.project.importing.MavenImportingManager;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;
import static org.assertj.core.api.Assertions.assertThat;

public class MavenImportWizardTest extends MavenProjectWizardTestCase {
  public void testImportModule() throws Exception {
    Path pom = createPom();
    Module module = importModuleFrom(new MavenProjectImportProvider(), pom.toString());
    if (MavenUtil.isLinearImportEnabled()) {
      afterImportFinished(myProject, c -> {
        List<Module> created = c.getContext().getModulesCreated();
        assertThat(created).singleElement().matches(m -> m.getName().equals("project"));
      });
    }
    else {
      assertEquals("project", module.getName());
    }
  }

  public void testImportProject() throws Exception {
    Path pom = createPom();
    Module module = importProjectFrom(pom.toString(), null, new MavenProjectImportProvider());

    afterImportFinished(module.getProject(), c -> {
      assertThat(ModuleManager.getInstance(c.getProject()).getModules()).hasOnlyOneElementSatisfying(
        m -> assertThat(m.getName()).isEqualTo("project"));
    });

    MavenGeneralSettings settings = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).getSettings().getGeneralSettings();
    MavenHomeType mavenHome = settings.getMavenHomeType();
    assertSame(BundledMaven3.INSTANCE, mavenHome);
    assertTrue(MavenProjectsNavigator.getInstance(module.getProject()).getGroupModules());
    assertTrue(settings.isUseMavenConfig());
  }

  public void testImportProjectWithWrapper() throws Exception {
    Path pom = createPom();
    createMavenWrapper(pom,
                       "distributionUrl=https://cache-redirector.jetbrains.com/repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.8.1/apache-maven-3.8.1-bin.zip");
    Module module = importProjectFrom(pom.toString(), null, new MavenProjectImportProvider());

    afterImportFinished(module.getProject(), c -> {
      assertThat(ModuleManager.getInstance(c.getProject()).getModules()).hasOnlyOneElementSatisfying(
        m -> assertThat(m.getName()).isEqualTo("project"));
    });
    MavenHomeType mavenHome =
      MavenWorkspaceSettingsComponent.getInstance(module.getProject()).getSettings().getGeneralSettings().getMavenHomeType();
    assertSame(MavenWrapper.INSTANCE, mavenHome);
  }


  public void testImportProjectWithWrapperWithoutUrl() throws Exception {
    Path pom = createPom();
    createMavenWrapper(pom, "property1=value1");
    Module module = importProjectFrom(pom.toString(), null, new MavenProjectImportProvider());
    MavenHomeType mavenHome =
      MavenWorkspaceSettingsComponent.getInstance(module.getProject()).getSettings().getGeneralSettings().getMavenHomeType();
    assertSame(BundledMaven3.INSTANCE, mavenHome);

    afterImportFinished(module.getProject(), c -> {
      assertThat(ModuleManager.getInstance(c.getProject()).getModules()).hasOnlyOneElementSatisfying(
        m -> assertThat(m.getName()).isEqualTo("project"));
    });
  }

  public void testImportProjectWithManyPoms() throws Exception {
    Path pom1 = createPom("pom1.xml");
    Path pom2 = pom1.getParent().resolve("pom2.xml");
    PathKt.write(pom2, MavenTestCase.createPomXml(
      """
        <groupId>test</groupId>
        <artifactId>project2</artifactId>
        <version>1</version>
        """));
    Module module = importProjectFrom(pom1.toString(), null, new MavenProjectImportProvider());
    if (MavenUtil.isLinearImportEnabled()) {
      afterImportFinished(getCreatedProject(), c -> {
        List<Path> paths = ContainerUtil.map(
          MavenProjectsManager.getInstance(c.getProject()).getProjectsTreeForTests().getExistingManagedFiles(), m -> m.toNioPath()
        );
        assertEquals(2, paths.size());
        assertContainsElements(paths, pom1, pom2);
      });
    }
    else {
      var project = module.getProject();
      var modules = ModuleManager.getInstance(project).getModules();
      var moduleNames = new HashSet<String>();
      for (var existingModule : modules) {
        moduleNames.add(existingModule.getName());
      }
      assertEquals(Set.of("project", "project2"), moduleNames);

      var projectsManager = MavenProjectsManager.getInstance(project);
      var mavenProjectNames = new HashSet<String>();
      for (var p : projectsManager.getProjects()) {
        mavenProjectNames.add(p.getMavenId().getArtifactId());
      }
      assertEquals(Set.of("project", "project2"), mavenProjectNames);
    }
  }

  public void testShouldStoreImlFileInSameDirAsPomXml() {
    Path dir = getTempDir().newPath("", true);
    String projectName = dir.toFile().getName();
    Path pom = dir.resolve("pom.xml");
    PathKt.write(pom, MavenTestCase.createPomXml(
      "<groupId>test</groupId>\n" +
      "<artifactId>\n" + projectName + "</artifactId>\n" +
      "<version>1</version>"));
    MavenProjectImportProvider provider = new MavenProjectImportProvider();
    Module module = importProjectFrom(pom.toString(), null, provider);
    Project project = module.getProject();
    waitForMavenImporting(project, LocalFileSystem.getInstance().findFileByNioFile(pom));
    ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false);

    Module[] modules = ModuleManager.getInstance(project).getModules();
    File imlFile = dir.resolve(projectName + ".iml").toFile();
    assertThat(modules).hasOnlyOneElementSatisfying(m ->
                                                      assertPathsEqual(m.getModuleFilePath(), imlFile.getAbsolutePath())
    );
  }

  private void waitForMavenImporting(@NotNull Project project, @NotNull VirtualFile file) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    if (!MavenUtil.isLinearImportEnabled()) {
      manager.waitForImportCompletion();
      MavenImportingTestCaseKt.importMavenProjects(manager, List.of(file));
    }

    Promise<?> promise = manager.waitForImportCompletion();
    PlatformTestUtil.waitForPromise(promise);
  }

  private static void createMavenWrapper(@NotNull Path pomPath, @NotNull String context) {
    Path fileName = pomPath.getParent().resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties");
    PathKt.write(fileName, context);
  }


  private void afterImportFinished(Project p, Consumer<MavenImportFinishedContext> after) {
    if (MavenUtil.isLinearImportEnabled()) {
      Promise<MavenImportFinishedContext> promise = MavenImportingManager.getInstance(p).getImportFinishPromise();
      PlatformTestUtil.waitForPromise(promise);
      try {
        after.accept(promise.blockingGet(0));
      }
      catch (TimeoutException | ExecutionException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
