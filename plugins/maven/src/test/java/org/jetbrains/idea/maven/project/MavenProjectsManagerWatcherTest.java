// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker;
import com.intellij.openapi.externalSystem.autoimport.ProjectNotificationAware;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.IOException;
import java.util.Collections;


public class MavenProjectsManagerWatcherTest extends MavenImportingTestCase {

  private MavenProjectsManager myProjectsManager;
  private ProjectNotificationAware myNotificationAware;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectsManager = MavenProjectsManager.getInstance(myProject);
    myNotificationAware = ProjectNotificationAware.getInstance(myProject);
    myProjectsManager.initForTests();
    myProjectsManager.enableAutoImportInTests();

    createProjectPom(createPomContent("test", "project"));
    addManagedFiles(myProjectPom);
  }

  public void testChangeConfigInAnotherProjectShouldNotUpdateOur() throws IOException {
    assertEmpty(myNotificationAware.getProjectsWithNotification());
    createPomFile(createProjectSubDir("../another"), createPomContent("another", "another"));
    VirtualFile mavenConfig = createProjectSubFile("../another/.mvn/maven.config");
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true");
    assertEmpty(myNotificationAware.getProjectsWithNotification());
  }

  public void testChangeConfigInOurProjectShouldCallUpdatePomFile() throws IOException {
    assertEmpty(myNotificationAware.getProjectsWithNotification());
    VirtualFile mavenConfig = createProjectSubFile(".mvn/maven.config");
    replaceContent(mavenConfig, "-Xmx2048m -Xms1024m -XX:MaxPermSize=512m -Djava.awt.headless=true");
    assertNotEmpty(myNotificationAware.getProjectsWithNotification());
  }

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

  private static String createPomContent(@NotNull String groupId, @NotNull String artifactId) {
    return String.format("<groupId>%s</groupId>\n<artifactId>%s</artifactId>\n<version>1.0-SNAPSHOT</version>", groupId, artifactId);
  }

  private void addManagedFiles(@NotNull VirtualFile pom) {
    myProjectsManager.addManagedFiles(Collections.singletonList(pom));
    myProjectsManager.waitForResolvingCompletion();
    myProjectsManager.performScheduledImportInTests();
  }

  private void replaceContent(@NotNull VirtualFile file, @NotNull String content) throws IOException {
    WriteCommandAction.runWriteCommandAction(myProject, (ThrowableComputable<?, IOException>)() -> {
      VfsUtil.saveText(file, content);
      return null;
    });
  }
}

