// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing;

import com.intellij.idea.IgnoreJUnit3;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

@IgnoreJUnit3(reason = "do not run on build server")
public abstract class MavenPerformanceTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath("C:\\projects\\mvn\\_projects\\geronimo\\pom.xml");
    initProjectsManager(false);
    myProjectsManager.resetManagedFilesAndProfilesInTests(Collections.singletonList(file), MavenExplicitProfiles.NONE);
  }

  @Test
  public void testReading() {
    measure(4000, () -> waitForReadingCompletion());
  }

  @Test
  public void testImporting() {
    waitForReadingCompletion();
    measure(8, () -> myProjectsManager.importProjects());
  }

  @Test
  public void testReImporting() {
    waitForReadingCompletion();
    myProjectsManager.importProjects();
    measure(2, () -> myProjectsManager.importProjects());
  }

  @Test
  public void testResolving() {
    waitForReadingCompletion();
    List<MavenProject> mavenProjects = myProjectsManager.getProjects();
    Collections.sort(mavenProjects, (o1, o2) -> o1.getPath().compareToIgnoreCase(o2.getPath()));

    myProjectsManager.unscheduleAllTasksInTests();

    myProjectsManager.scheduleResolveInTests(mavenProjects.subList(0, 100));
    measure(50000, () -> myProjectsManager.waitForResolvingCompletion());
  }

  private static void measure(long expected, Runnable r) {
    //ProfilingUtil.startCPUProfiling();
    long before = System.currentTimeMillis();
    r.run();
    long after = System.currentTimeMillis();
    long timing = after - before;
    //System.out.println(getName() + ": " + timing + " ->\n" + ProfilingUtil.captureCPUSnapshot());
    //ProfilingUtil.stopCPUProfiling();
    assertTrue(timing < expected);
  }
}
