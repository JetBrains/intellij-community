package org.jetbrains.idea.maven.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.junit.Test;

import java.io.IOException;

public class MavenDontExcludeTargetTest extends MavenMultiVersionImportingTestCase {
  public void testDontExcludeTargetTest() throws IOException {
    MavenProjectsManager.getInstance(myProject).getImportingSettings().setExcludeTargetFolder(false);

    VirtualFile classA = createProjectSubFile("target/classes/A.class");
    VirtualFile testClass = createProjectSubFile("target/test-classes/ATest.class");

    VirtualFile a = createProjectSubFile("target/a.txt");
    VirtualFile aaa = createProjectSubFile("target/aaa/a.txt");

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();

    assert !fileIndex.isInContent(classA);
    assert !fileIndex.isInContent(testClass);
    assert fileIndex.isInContent(a);
    assert fileIndex.isInContent(aaa);
  }

  @Test
  public void testDontExcludeTargetTest2() throws IOException {
    MavenProjectsManager.getInstance(myProject).getImportingSettings().setExcludeTargetFolder(false);

    VirtualFile realClassA = createProjectSubFile("customOutput/A.class");
    VirtualFile realTestClass = createProjectSubFile("customTestOutput/ATest.class");

    VirtualFile classA = createProjectSubFile("target/classes/A.class");
    VirtualFile testClass = createProjectSubFile("target/test-classes/ATest.class");

    VirtualFile a = createProjectSubFile("target/a.txt");
    VirtualFile aaa = createProjectSubFile("target/aaa/a.txt");

    importProject(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

        <build>
        <outputDirectory>customOutput</outputDirectory>
        <testOutputDirectory>customTestOutput</testOutputDirectory>
        </build>
        """);

    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();

    assert fileIndex.isInContent(classA);
    assert fileIndex.isInContent(testClass);
    assert fileIndex.isInContent(a);
    assert fileIndex.isInContent(aaa);
    assert !fileIndex.isInContent(realClassA);
    assert !fileIndex.isInContent(realTestClass);
  }
}
