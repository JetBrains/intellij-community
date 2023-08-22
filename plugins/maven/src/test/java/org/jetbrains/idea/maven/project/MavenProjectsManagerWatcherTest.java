// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.MavenProjectLegacyImporter;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.*;


public class MavenProjectsManagerWatcherTest extends MavenMultiVersionImportingTestCase {

  private MavenProjectsManager myProjectsManager;
  private MavenProjectTreeTracker myProjectsTreeTracker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectsManager = MavenProjectsManager.getInstance(myProject);
    myProjectsTreeTracker = new MavenProjectTreeTracker();

    myProjectsManager.addProjectsTreeListener(myProjectsTreeTracker, getTestRootDisposable());

    initProjectsManager(true);

    createProjectPom(createPomContent("test", "project"));
    importProject();
    //addManagedFiles(myProjectPom);
  }

  @Test
  public void testChangeConfigInAnotherProjectShouldNotUpdateOur() throws IOException {
    assertNoPendingProjectForReload();

    createPomFile(createProjectSubDir("../another"), createPomContent("another", "another"));
    assertNoPendingProjectForReload();

    VirtualFile mavenConfig = createProjectSubFile("../another/.mvn/maven.config");
    assertNoPendingProjectForReload();

    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true");
    assertNoPendingProjectForReload();
  }

  @Test
  public void testChangeConfigInOurProjectShouldCallUpdatePomFile() throws Exception {
    assertNoPendingProjectForReload();

    VirtualFile mavenConfig = createProjectSubFile(".mvn/maven.config");
    importProject();
    assertNoPendingProjectForReload();

    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true");
    assertHasPendingProjectForReload();

    scheduleProjectImportAndWait();
  }

  @Test
  public void testChangeConfigInAnotherProjectShouldCallItIfItWasAdded() throws IOException {
    assertNoPendingProjectForReload();

    VirtualFile anotherPom = createPomFile(createProjectSubDir("../another"), createPomContent("another", "another"));
    VirtualFile mavenConfig = createProjectSubFile("../another/.mvn/maven.config");
    assertNoPendingProjectForReload();

    addManagedFiles(anotherPom);
    assertNoPendingProjectForReload();

    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true");
    assertHasPendingProjectForReload();

    scheduleProjectImportAndWait();
  }

  @Test
  public void testSaveDocumentChangesBeforeAutoImport() throws IOException {
    assertNoPendingProjectForReload();

    assertModules("project");

    replaceContent(myProjectPom, createPomXml(
      createPomContent("test", "project") + "<packaging>pom</packaging>\n<modules><module>module</module></modules>"));
    createModulePom("module", createPomContent("test", "module"));
    scheduleProjectImportAndWait();

    assertModules("project", "module");

    replaceDocumentString(myProjectPom, "<modules><module>module</module></modules>", "");

    //configConfirmationForYesAnswer();
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true);

    scheduleProjectImportAndWait();

    assertModules("project");
  }

  @Test
  public void testIncrementalAutoReload() {
    assertRootProjects("project");
    assertNoPendingProjectForReload();

    VirtualFile module1 = createModulePom("module1", createPomContent("test", "module1"));
    VirtualFile module2 = createModulePom("module2", createPomContent("test", "module2"));

    assertRootProjects("project");
    assertNoPendingProjectForReload();

    addManagedFiles(module1);
    addManagedFiles(module2);

    assertRootProjects("project", "module1", "module2");
    assertNoPendingProjectForReload();

    replaceDocumentString(module1, "test", "group.id");

    myProjectsTreeTracker.reset();
    scheduleProjectImportAndWait();
    assertEquals(0, myProjectsTreeTracker.getProjectStatus("project").updateCounter);
    assertEquals(1, myProjectsTreeTracker.getProjectStatus("module1").updateCounter);
    assertEquals(0, myProjectsTreeTracker.getProjectStatus("module2").updateCounter);

    replaceDocumentString(module2, "test", "group.id");

    myProjectsTreeTracker.reset();
    scheduleProjectImportAndWait();
    assertEquals(0, myProjectsTreeTracker.getProjectStatus("project").updateCounter);
    assertEquals(0, myProjectsTreeTracker.getProjectStatus("module1").updateCounter);
    assertEquals(1, myProjectsTreeTracker.getProjectStatus("module2").updateCounter);

    replaceDocumentString(module1, "group.id", "test");
    replaceDocumentString(module2, "group.id", "test");

    myProjectsTreeTracker.reset();
    scheduleProjectImportAndWait();
    assertEquals(0, myProjectsTreeTracker.getProjectStatus("project").updateCounter);
    assertEquals(1, myProjectsTreeTracker.getProjectStatus("module1").updateCounter);
    assertEquals(1, myProjectsTreeTracker.getProjectStatus("module2").updateCounter);
  }

  @Test
  public void testProfilesAutoReload() {
    createProjectPom("""
                         <groupId>test</groupId>
                         <artifactId>project</artifactId>
                         <version>1</version>
                         
                         <profiles>
                             <profile>
                                 <id>junit4</id>
                                 <dependencies>
                                     <dependency>
                                         <groupId>junit</groupId>
                                         <artifactId>junit</artifactId>
                                         <version>4.12</version>
                                         <scope>test</scope>
                                     </dependency>
                                 </dependencies>
                             </profile>
                             <profile>
                                 <id>junit5</id>
                                 <dependencies>
                                     <dependency>
                                         <groupId>org.junit.jupiter</groupId>
                                         <artifactId>junit-jupiter-engine</artifactId>
                                         <version>5.9.1</version>
                                         <scope>test</scope>
                                     </dependency>
                                 </dependencies>
                             </profile>
                         </profiles>
                       """);
    scheduleProjectImportAndWait();
    assertRootProjects("project");
    assertModules("project");

    myProjectsManager.setExplicitProfiles(new MavenExplicitProfiles(List.of("junit4"), List.of("junit5")));
    assertHasPendingProjectForReload();

    scheduleProjectImportAndWait();
    assertMavenProjectDependencies("test:project:1", "junit:junit:4.12");

    myProjectsManager.setExplicitProfiles(new MavenExplicitProfiles(List.of("junit5"), List.of("junit4")));
    assertHasPendingProjectForReload();

    scheduleProjectImportAndWait();
    assertMavenProjectDependencies("test:project:1", "org.junit.jupiter:junit-jupiter-engine:5.9.1");
  }

  private void assertMavenProjectDependencies(@NotNull String projectMavenCoordinates, String... expectedDependencies) {
    var mavenId = new MavenId(projectMavenCoordinates);
    var mavenProject = myProjectsManager.getProjectsTree().findProject(mavenId);
    var actualDependencies = ContainerUtil.map(mavenProject.getDependencyTree(), it -> it.getArtifact().getMavenId().getKey());
    Assert.assertEquals(List.of(expectedDependencies), actualDependencies);
  }

  private static String createPomContent(@NotNull String groupId, @NotNull String artifactId) {
    return String.format("<groupId>%s</groupId>\n<artifactId>%s</artifactId>\n<version>1.0-SNAPSHOT</version>", groupId, artifactId);
  }

  private void addManagedFiles(@NotNull VirtualFile pom) {
    myProjectsManager.addManagedFiles(Collections.singletonList(pom));
    waitForImportCompletion();
    if (!isNewImportingProcess) {
      //myProjectsManager.performScheduledImportInTests();
    }
  }

  private void replaceContent(@NotNull VirtualFile file, @NotNull String content) throws IOException {
    WriteCommandAction.runWriteCommandAction(myProject, (ThrowableComputable<?, IOException>)() -> {
      VfsUtil.saveText(file, content);
      return null;
    });
  }

  protected void replaceDocumentString(VirtualFile file, String oldString, String newString) {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getDocument(file);
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      String text = document.getText();
      int startOffset = text.indexOf(oldString);
      int endOffset = startOffset + oldString.length();
      document.replaceString(startOffset, endOffset, newString);
    });
  }

static class MavenProjectTreeTracker implements MavenProjectsTree.Listener {
  private final Map<String, MavenProjectStatus> projects = new HashMap<>();

  public MavenProjectStatus getProjectStatus(String artifactId) {
    return projects.computeIfAbsent(artifactId, __ -> new MavenProjectStatus());
  }

  public void reset() {
    projects.clear();
  }

  @Override
  public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
    for (Pair<MavenProject, MavenProjectChanges> it : updated) {
      String artifactId = it.first.getMavenId().getArtifactId();
      MavenProjectStatus projectStatus = getProjectStatus(artifactId);
      projectStatus.updateCounter++;
    }
    for (MavenProject mavenProject : deleted) {
      String artifactId = mavenProject.getMavenId().getArtifactId();
      MavenProjectStatus projectStatus = getProjectStatus(artifactId);
      projectStatus.deleteCounter++;
    }
  }
}

static class MavenProjectStatus {
  int updateCounter = 0;
  int deleteCounter = 0;
}
}

