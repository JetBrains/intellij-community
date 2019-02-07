// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;

import java.io.IOException;
import java.util.Collections;


public class MavenProjectsManagerWatcherTest extends MavenTestCase {

  private MavenProjectsManagerWatcher myProjectManagerWatcher;
  private MavenProjectsProcessor myProjectsProcessor;
  private MavenProjectsTree myProjectsTree;
  private MavenProjectsManagerWatcher.MyFileChangeListener myListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MavenEmbeddersManager mavenEmbeddersManager = new MavenEmbeddersManager(myProject);
    myProjectsProcessor = new MavenProjectsProcessor(myProject, "Project", true, mavenEmbeddersManager);
    myProjectsTree = new MavenProjectsTree(myProject);
    myProjectManagerWatcher = new MavenProjectsManagerWatcher(myProject, MavenProjectsManager.getInstance(myProject),
                                                              myProjectsTree,
                                                              new MavenGeneralSettings(),
                                                              myProjectsProcessor,
                                                              mavenEmbeddersManager
    );
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>");
    myProjectsTree
      .addManagedFilesWithProfiles(Collections.singletonList(myProjectPom), new MavenExplicitProfiles(Collections.emptyList()));
    myListener = myProjectManagerWatcher.new MyFileChangeListener();
  }

  public void testChangeConfigInAnotherProjectShouldNotUpdateOur() throws IOException {

    createPomFile(createProjectSubDir("../another"), "<groupId>another</groupId>" +
                                                     "<artifactId>another</artifactId>" +
                                                     "<version>1</version>");
    VirtualFile mavenConfig = createProjectSubFile("../another/.mvn/maven.config");


    updateFile(mavenConfig);
    assertEmpty(myListener.getFilesToUpdate());
  }

  private void updateFile(VirtualFile file) {
    myListener.updateFile(file, new VFileContentChangeEvent(this, file, 0, LocalTimeCounter.currentTime(), false));
  }

  public void testChangeConfigInOurProjectShouldCallUpdatePomFile() throws IOException {
    VirtualFile mavenConfig = createProjectSubFile(".mvn/maven.config");
    updateFile(mavenConfig);
    assertContain(myListener.getFilesToUpdate(), myProjectPom);
  }

  public void testChangeConfigInAnotherProjectShouldCallItIfItWasAdded() throws IOException {
    VirtualFile anotherPom = createPomFile(createProjectSubDir("../another"), "<groupId>another</groupId>" +
                                                                              "<artifactId>another</artifactId>" +
                                                                              "<version>1</version>");
    VirtualFile mavenConfig = createProjectSubFile("../another/.mvn/maven.config");

    myProjectsTree
      .addManagedFilesWithProfiles(Collections.singletonList(anotherPom), new MavenExplicitProfiles(Collections.emptyList()));

    updateFile(mavenConfig);
    assertContain(myListener.getFilesToUpdate(), anotherPom);
    assertDoNotContain(myListener.getFilesToUpdate(), myProjectPom);
  }
}

