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
package org.jetbrains.idea.maven.execution;

import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.util.Arrays;

public class MavenRunnerTest extends MavenImportingTestCase {
  public void testUpdatingExcludedFoldersAfterRun() throws Exception {
    if (!hasMavenInstallation()) return;

    createStdProjectFolders();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertModules("project");
    assertExcludes("project", "target");

    createProjectSubDirs("target/generated-sources/foo",
                         "target/bar");

    MavenRunner runner = new MavenRunner(myProject);
    MavenRunnerParameters params = new MavenRunnerParameters(true, getProjectPath(), Arrays.asList("compile"), null);
    MavenRunnerSettings settings = new MavenRunnerSettings();
    runner.run(params, settings);

    assertSources("project",
                  "src/main/java",
                  "src/main/resources",
                  "target/generated-sources/foo");

    assertExcludes("project",
                   "target/bar",
                   "target/classes",
                   "target/classes"); // output dirs are collected twice for exclusion and for compiler output
  }
}
