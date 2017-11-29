/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class IgnoresImportingTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(false);
  }

  public void testDoNotImportIgnoredProjects() {
    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");

    myProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p1.getPath()));
    importProjects(p1, p2);
    assertModules("project2");
  }

  public void testAddingAndRemovingModulesWhenIgnoresChange() {
    configConfirmationForYesAnswer();

    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");
    importProjects(p1, p2);
    assertModules("project1", "project2");

    myProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p1.getPath()));
    waitForReadingCompletion();
    myProjectsManager.performScheduledImportInTests();

    assertModules("project2");

    myProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p2.getPath()));
    waitForReadingCompletion();
    myProjectsManager.performScheduledImportInTests();

    assertModules("project1");
  }

  public void testDoNotAskTwiceToRemoveIgnoredModule() {
    AtomicInteger counter = configConfirmationForNoAnswer();

    VirtualFile p1 = createModulePom("project1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile p2 = createModulePom("project2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>project2</artifactId>" +
                                     "<version>1</version>");
    importProjects(p1, p2);
    assertModules("project1", "project2");

    myProjectsManager.setIgnoredFilesPaths(Collections.singletonList(p1.getPath()));
    waitForReadingCompletion();
    myProjectsManager.performScheduledImportInTests();

    assertModules("project1", "project2");
    assertEquals(1, counter.get());

    waitForReadingCompletion();
    myProjectsManager.performScheduledImportInTests();

    assertModules("project1", "project2");
    assertEquals(1, counter.get());
  }
}
