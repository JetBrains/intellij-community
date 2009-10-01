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
