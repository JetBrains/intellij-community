// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.actions;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.testFramework.TestActionEvent;
import org.junit.Test;

import java.util.Collections;

public class ReimportProjectActionTest extends MavenMultiVersionImportingTestCase {
  @Test
  public void testReloadOnlyVisibleForNonIgnoredProjects() {
    var parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """);
    var m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """);
    importProject();

    var action = new ReimportProjectAction();
    var parentActionVisible = action.isVisible(new TestActionEvent(createTestDataContext(parentFile), action));
    var m1ActionVisible = action.isVisible(new TestActionEvent(createTestDataContext(m1File), action));

    assertTrue(parentActionVisible);
    assertTrue(m1ActionVisible);

    var module = getModule("m1");
    assertNotNull(module);
    var mavenProject = myProjectsManager.findProject(module);
    assertFalse(myProjectsManager.isIgnored(mavenProject));
    myProjectsManager.setIgnoredState(Collections.singletonList(mavenProject), true);
    assertTrue(myProjectsManager.isIgnored(mavenProject));

    var m1IgnoredActionVisible = action.isVisible(new TestActionEvent(createTestDataContext(m1File), action));
    assertFalse(m1IgnoredActionVisible);
  }
}
