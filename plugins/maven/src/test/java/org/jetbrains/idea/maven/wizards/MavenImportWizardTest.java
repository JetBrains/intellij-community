// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.server.MavenServerConnector;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.io.IOException;
import java.nio.file.Path;

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
    String mavenHome = MavenWorkspaceSettingsComponent.getInstance(module.getProject()).getSettings().getGeneralSettings().getMavenHome();
    assertEquals(MavenServerManager.BUNDLED_MAVEN_3, mavenHome);
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

  private @NotNull Path createPom() throws IOException {
    return createTempFile("pom.xml", MavenTestCase.createPomXml("<groupId>test</groupId>" +
                                                                "<artifactId>project</artifactId>" +
                                                                "<version>1</version>")).toPath();
  }

  private static void createMavenWrapper(@NotNull Path pomPath, @NotNull String context) {
    Path fileName = pomPath.getParent().resolve(".mvn").resolve("wrapper").resolve("maven-wrapper.properties");
    PathKt.write(fileName, context);
  }
}
