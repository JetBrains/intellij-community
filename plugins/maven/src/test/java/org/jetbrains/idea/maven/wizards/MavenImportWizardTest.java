// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import com.intellij.maven.testFramework.MavenTestCase;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenImportWizardTest extends ProjectWizardTestCase<AbstractProjectWizard> {
  @Override
  public void tearDown() throws Exception {
    try {
      MavenServerManager.getInstance().shutdown(true);
      JavaAwareProjectJdkTableImpl.removeInternalJdkInTests();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testImportModule() throws Exception {
    Path pom = createPom();
    Module module = importModuleFrom(new MavenProjectImportProvider(), pom.toString());
    assertEquals("project", module.getName());
  }

  public void testImportProject() throws Exception {
    Path pom = createPom();
    Module module = importProjectFrom(pom.toString(), null, new MavenProjectImportProvider());
    assertThat(module.getName()).isEqualTo("project");
    MavenGeneralSettings settings = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).getSettings().getGeneralSettings();
    String mavenHome = settings.getMavenHome();
    assertEquals(MavenServerManager.BUNDLED_MAVEN_3, mavenHome);
    assertTrue(MavenProjectsNavigator.getInstance(module.getProject()).getGroupModules());
    assertTrue(settings.isUseMavenConfig());
  }

  public void testImportProjectWithWrapper() throws Exception {
    Path pom = createPom();
    createMavenWrapper(pom, "distributionUrl=wrapper-url");
    Module module = importProjectFrom(pom.toString(), null, new MavenProjectImportProvider());
    assertThat(module.getName()).isEqualTo("project");
    String mavenHome = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).getSettings().getGeneralSettings().getMavenHome();
    assertEquals(MavenServerManager.WRAPPED_MAVEN, mavenHome);
  }

  public void testImportProjectWithWrapperWithoutUrl() throws Exception {
    Path pom = createPom();
    createMavenWrapper(pom, "property1=value1");
    Module module = importProjectFrom(pom.toString(), null, new MavenProjectImportProvider());
    assertThat(module.getName()).isEqualTo("project");
    String mavenHome = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).getSettings().getGeneralSettings().getMavenHome();
    assertEquals(MavenServerManager.BUNDLED_MAVEN_3, mavenHome);
  }

  public void testImportProjectWithManyPoms() throws Exception {
    Path pom1 = createPom("pom1.xml");
    Path pom2 = pom1.getParent().resolve("pom2.xml");
    PathKt.write(pom2, MavenTestCase.createPomXml(
      "<groupId>test</groupId>" +
      "<artifactId>project2</artifactId>" +
      "<version>1</version>"));
    Module module = importProjectFrom(pom1.toString(), null, new MavenProjectImportProvider());
    List<Path> paths = ContainerUtil.map(
      MavenProjectsManager.getInstance(module.getProject()).getProjectsTreeForTests().getExistingManagedFiles(), m -> m.toNioPath()
    );
    assertEquals(2, paths.size());
    assertContainsElements(paths, pom1, pom2);
  }

  public void testImportProjectWithDirectPom() throws Exception {
    Path pom1 = createPom();
    Path pom2 = pom1.getParent().resolve("pom2.xml");
    PathKt.write(pom2, MavenTestCase.createPomXml(
      "<groupId>test</groupId>" +
      "<artifactId>project2</artifactId>" +
      "<version>1</version>"));
    MavenProjectImportProvider provider = new MavenProjectImportProvider();
    MavenProjectBuilder builder = (MavenProjectBuilder)provider.doGetBuilder();
    builder.setFileToImport(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pom2));
    Module module = importProjectFrom(pom1.toString(), null, provider);
    List<Path> paths = ContainerUtil.map(
      MavenProjectsManager.getInstance(module.getProject()).getProjectsTreeForTests().getExistingManagedFiles(), m -> m.toNioPath()
    );
    assertEquals(1, paths.size());
    assertContainsElements(paths, pom2);
  }

  private @NotNull Path createPom() throws IOException {
    return createPom("pom.xml");
  }

  private @NotNull Path createPom(String pomName) throws IOException {
    return createTempFile(pomName, MavenTestCase.createPomXml("<groupId>test</groupId>" +
                                                              "<artifactId>project</artifactId>" +
                                                              "<version>1</version>")).toPath();
  }

  private static void createMavenWrapper(@NotNull Path pomPath, @NotNull String context) {
    Path fileName = pomPath.getParent().resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties");
    PathKt.write(fileName, context);
  }
}
