// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MavenProjectsManagerWatcherTest extends MavenMultiVersionImportingTestCase {

  private MavenProjectsManager myProjectsManager;
  private AutoImportProjectNotificationAware myNotificationAware;
  private ExternalSystemProjectTracker myProjectTracker;
  private MavenProjectTreeTracker myProjectsTreeTracker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectsManager = MavenProjectsManager.getInstance(myProject);
    myNotificationAware = AutoImportProjectNotificationAware.getInstance(myProject);
    myProjectTracker = ExternalSystemProjectTracker.getInstance(myProject);
    myProjectsTreeTracker = new MavenProjectTreeTracker();

    myProjectsManager.addProjectsTreeListener(myProjectsTreeTracker, getTestRootDisposable());

    initProjectsManager(true);

    createProjectPom(createPomContent("test", "project"));
    addManagedFiles(myProjectPom);
  }

  @Test
  public void testChangeConfigInAnotherProjectShouldNotUpdateOur() throws IOException {
    assertEmpty(myNotificationAware.getProjectsWithNotification());
    createPomFile(createProjectSubDir("../another"), createPomContent("another", "another"));
    VirtualFile mavenConfig = createProjectSubFile("../another/.mvn/maven.config");
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true");
    assertEmpty(myNotificationAware.getProjectsWithNotification());
  }

  @Test 
  public void testChangeConfigInOurProjectShouldCallUpdatePomFile() throws IOException {
    assertEmpty(myNotificationAware.getProjectsWithNotification());
    VirtualFile mavenConfig = createProjectSubFile(".mvn/maven.config");
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true");
    assertNotEmpty(myNotificationAware.getProjectsWithNotification());
  }

  @Test 
  public void testChangeConfigInAnotherProjectShouldCallItIfItWasAdded() throws IOException {
    assertEmpty(myNotificationAware.getProjectsWithNotification());
    VirtualFile anotherPom = createPomFile(createProjectSubDir("../another"), createPomContent("another", "another"));
    VirtualFile mavenConfig = createProjectSubFile("../another/.mvn/maven.config");
    assertEmpty(myNotificationAware.getProjectsWithNotification());
    addManagedFiles(anotherPom);
    assertEmpty(myNotificationAware.getProjectsWithNotification());
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true");
    assertNotEmpty(myNotificationAware.getProjectsWithNotification());
  }

  @Test 
  public void testSaveDocumentChangesBeforeAutoImport() throws IOException {
    assertEmpty(myNotificationAware.getProjectsWithNotification());

    assertModules("project");

    replaceContent(myProjectPom, createPomXml(createPomContent("test", "project") + "\n<modules><module>module</module></modules>"));
    createModulePom("module", createPomContent("test", "module"));
    scheduleProjectImportAndWait();

    assertModules("project", "module");

    replaceDocumentString(myProjectPom, "<modules><module>module</module></modules>", "");
    configConfirmationForYesAnswer();
    scheduleProjectImportAndWait();

    assertModules("project");
  }

  @Test 
  public void testIncrementalAutoReload() {
    assertRootProjects("project");
    assertFalse(myNotificationAware.isNotificationVisible());

    VirtualFile module1 = createModulePom("module1", createPomContent("test", "module1"));
    VirtualFile module2 = createModulePom("module2", createPomContent("test", "module2"));

    assertRootProjects("project");
    assertFalse(myNotificationAware.isNotificationVisible());

    addManagedFiles(module1);
    addManagedFiles(module2);

    assertRootProjects("project", "module1", "module2");
    assertFalse(myNotificationAware.isNotificationVisible());

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

  private void scheduleProjectImportAndWait() {
    assertTrue(myNotificationAware.isNotificationVisible());
    myProjectTracker.scheduleProjectRefresh();
    waitForImportCompletion();
    MavenUtil.invokeAndWait(myProject, () -> {
      // Do not save documents here, MavenProjectAware should do this before import
      myProjectsManager.performScheduledImportInTests();
    });
    assertFalse(myNotificationAware.isNotificationVisible());
  }

  private static String createPomContent(@NotNull String groupId, @NotNull String artifactId) {
    return String.format("<groupId>%s</groupId>\n<artifactId>%s</artifactId>\n<version>1.0-SNAPSHOT</version>", groupId, artifactId);
  }

  private void addManagedFiles(@NotNull VirtualFile pom) {
    myProjectsManager.addManagedFiles(Collections.singletonList(pom));
    waitForImportCompletion();
    myProjectsManager.performScheduledImportInTests();
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

