// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author ibessonov
 */
public class MavenConfigImportingTest extends MavenDomTestCase {

  @Test
  public void testResolveJvmConfigProperty() throws IOException {
    createProjectSubFile(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "-Dver=1");
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${ver}</version>""");

    MavenProject mavenProject = myProjectsManager.findProject(getModule("project"));
    assertEquals("1", mavenProject.getMavenId().getVersion());
  }

  @Test
  public void testResolveMavenConfigProperty() throws IOException {
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1");
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${ver}</version>""");

    MavenProject mavenProject = myProjectsManager.findProject(getModule("project"));
    assertEquals("1", mavenProject.getMavenId().getVersion());
  }

  @Test
  public void testResolvePropertyPriority() throws IOException {
    createProjectSubFile(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "-Dver=ignore");
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1");
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${ver}</version>
                    <properties>
                      <ver>ignore</ver></properties>""");

    MavenProject mavenProject = myProjectsManager.findProject(getModule("project"));
    assertEquals("1", mavenProject.getMavenId().getVersion());
  }

  @Test
  public void testResolveConfigPropertiesInModules() throws IOException {
    assumeVersionMoreThan("3.3.1");
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1 -DmoduleName=m1");

    createModulePom("m1", """
      <artifactId>${moduleName}</artifactId>
      <version>${ver}</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>${ver}</version>
      </parent>""");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${ver}</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>${moduleName}</module></modules>""");

    MavenProject mavenProject = myProjectsManager.findProject(getModule("project"));
    assertEquals("1", mavenProject.getMavenId().getVersion());

    MavenProject module = myProjectsManager.findProject(getModule(mn("project", "m1")));
    assertNotNull(module);

    assertEquals("m1", module.getMavenId().getArtifactId());
    assertEquals("1", module.getMavenId().getVersion());
  }

  @Test
  public void testMavenConfigCompletion() throws Exception {
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dconfig.version=1");
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>""");

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>${config.<caret></version>""");

    assertCompletionVariants(myProjectPom, "config.version");
  }

  @Test
  public void testMavenConfigReferenceResolving() throws IOException {
    createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dconfig.version=1");
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${config.version}</version>""");

    PsiElement resolvedReference = getReference(myProjectPom, "config.version", 0).resolve();
    assertNotNull(resolvedReference);

    assertInstanceOf(resolvedReference, MavenPsiElementWrapper.class);
    assertEquals("1", ((MavenPsiElementWrapper)resolvedReference).getName());
  }

  @Test
  public void testReimportOnConfigChange() throws IOException {
    VirtualFile configFile = createProjectSubFile(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "-Dver=1");
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>${ver}</version>""");

    MavenProject mavenProject = myProjectsManager.findProject(getModule("project"));
    assertEquals("1", mavenProject.getMavenId().getVersion());

    WriteAction.runAndWait(() -> {
      byte[] content = "-Dver=2".getBytes(StandardCharsets.UTF_8);
      configFile.setBinaryContent(content, -1, configFile.getTimeStamp() + 1);
    });
    configConfirmationForYesAnswer();
    importProject();

    mavenProject = myProjectsManager.findProject(getModule("project"));
    assertEquals("2", mavenProject.getMavenId().getVersion());
  }
}
