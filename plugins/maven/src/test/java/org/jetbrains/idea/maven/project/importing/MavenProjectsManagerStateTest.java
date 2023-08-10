// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProjectsManagerState;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.junit.Assume;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MavenProjectsManagerStateTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(true);
    Assume.assumeFalse(MavenUtil.isLinearImportEnabled());
  }

  @Test
  public void testSavingAndLoadingState() {
    MavenProjectsManagerState state = myProjectsManager.getState();
    assertTrue(state.originalFiles.isEmpty());
    assertTrue(MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().enabledProfiles.isEmpty());
    assertTrue(state.ignoredFiles.isEmpty());
    assertTrue(state.ignoredPathMasks.isEmpty());

    VirtualFile p1 = createModulePom("project1",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       <profiles>
                                        <profile>
                                         <id>one</id>
                                        </profile>
                                        <profile>
                                         <id>two</id>
                                        </profile>
                                        <profile>
                                         <id>three</id>
                                        </profile>
                                       </profiles>
                                       """);

    VirtualFile p2 = createModulePom("project2",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>project2</artifactId>
                                       <version>1</version>
                                       <packaging>pom</packaging>
                                       <modules>
                                         <module>../project3</module>
                                       </modules>
                                       """);

    VirtualFile p3 = createModulePom("project3",
                                     """
                                       <groupId>test</groupId>
                                       <artifactId>project3</artifactId>
                                       <version>1</version>
                                       """);

    importProjects(p1, p2);
    myProjectsManager.setExplicitProfiles(new MavenExplicitProfiles(Arrays.asList("one", "two")));
    setIgnoredFilesPathForNextImport(Arrays.asList(p1.getPath()));
    setIgnoredPathPatternsForNextImport(Arrays.asList("*.xxx"));

    state = myProjectsManager.getState();
    assertUnorderedPathsAreEqual(state.originalFiles, Arrays.asList(p1.getPath(), p2.getPath()));
    assertUnorderedElementsAreEqual(MavenWorkspaceSettingsComponent.getInstance(myProject).getState().enabledProfiles, "one", "two");
    assertUnorderedPathsAreEqual(state.ignoredFiles, Arrays.asList(p1.getPath()));
    assertUnorderedElementsAreEqual(state.ignoredPathMasks, "*.xxx");

    MavenProjectsManagerState newState = new MavenProjectsManagerState();

    newState.originalFiles = Arrays.asList(p1.getPath(), p3.getPath());
    MavenWorkspaceSettingsComponent.getInstance(myProject).getSettings().setEnabledProfiles(Arrays.asList("three"));
    newState.ignoredFiles = Collections.singleton(p1.getPath());
    newState.ignoredPathMasks = Arrays.asList("*.zzz");

    myProjectsManager.loadState(newState);

    assertUnorderedPathsAreEqual(myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths(),
                                 Arrays.asList(p1.getPath(), p3.getPath()));
    assertUnorderedElementsAreEqual(myProjectsManager.getExplicitProfiles().getEnabledProfiles(), "three");
    assertUnorderedPathsAreEqual(myProjectsManager.getIgnoredFilesPaths(), Arrays.asList(p1.getPath()));
    assertUnorderedElementsAreEqual(myProjectsManager.getIgnoredFilesPatterns(), "*.zzz");

    waitForReadingCompletion();
    assertUnorderedElementsAreEqual(myProjectsManager.getProjectsTreeForTests().getRootProjectsFiles(),
                                    p1, p3);
  }
}
