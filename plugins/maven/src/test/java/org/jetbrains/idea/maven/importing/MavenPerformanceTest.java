/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.idea.Bombed;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProject;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

@Bombed(user = "cdr", year = 3000, month = Calendar.FEBRUARY, day = 1, description = "do not run on build server")
public abstract class MavenPerformanceTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath("C:\\projects\\mvn\\_projects\\geronimo\\pom.xml");
    initProjectsManager(false);
    myProjectsManager.resetManagedFilesAndProfilesInTests(Collections.singletonList(file), MavenExplicitProfiles.NONE);
  }

  public void testReading() {
    measure(4000, () -> waitForReadingCompletion());
  }

  public void testImporting() {
    waitForReadingCompletion();
    measure(8, () -> myProjectsManager.importProjects());
  }

  public void testReImporting() {
    waitForReadingCompletion();
    myProjectsManager.importProjects();
    measure(2, () -> myProjectsManager.importProjects());
  }

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
    //System.out.println(getName() + ": " + timing + " ->" + ProfilingUtil.captureCPUSnapshot());
    //ProfilingUtil.stopCPUProfiling();
    assertTrue(timing < expected);
  }
}
