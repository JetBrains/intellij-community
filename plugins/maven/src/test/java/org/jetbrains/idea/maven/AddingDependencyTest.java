package org.jetbrains.idea.maven;

import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.idea.maven.indices.MavenCustomRepositoryHelper;
import org.jetbrains.idea.maven.project.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenId;

import java.io.File;
import java.util.List;

public class AddingDependencyTest extends MavenImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initProjectsManager(true);
    MavenCustomRepositoryHelper helper = new MavenCustomRepositoryHelper(myDir, "local1");
    setRepositoryPath(helper.getTestDataPath("local1"));
  }

  public void testBasics() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myProjectsManager.addDependency(myProjectsTree.findProject(myProjectPom),
                                    new MavenId("junit", "junit", "4.0"));
    myProjectsManager.performScheduledImport();

    List<MavenArtifact> deps = myProjectsTree.getProjects().get(0).getDependencies();
    assertEquals(1, deps.size());
    assertEquals(new File(getRepositoryPath(), "junit/junit/4.0/junit-4.0.jar"),
                 deps.get(0).getFile());

    importProject();
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testAddingToInvalid() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version");

    myProjectsManager.addDependency(myProjectsTree.findProject(myProjectPom),
                                    new MavenId("junit", "junit", "4.0"));
    myProjectsManager.performScheduledImport();


    List<MavenArtifact> deps = myProjectsTree.getProjects().get(0).getDependencies();
    assertEquals(1, deps.size());
    assertEquals(new File(getRepositoryPath(), "junit/junit/4.0/junit-4.0.jar"),
                 deps.get(0).getFile());

    importProject();
    assertModuleLibDeps("project", "Maven: junit:junit:4.0");
  }

  public void testDownloadingBeforeAdding() throws Exception {
    File jarFile = new File(getRepositoryPath(), "junit/junit/4.0/junit-4.0.jar");

    removeFromLocalRepository("junit");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertFalse(jarFile.exists());

    myProjectsManager.addDependency(myProjectsTree.findProject(myProjectPom),
                                    new MavenId("junit", "junit", "4.0"));
    myProjectsManager.performScheduledImport();

    assertTrue(jarFile.exists());
  }

  public void testClearingROStatus() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    ReadOnlyAttributeUtil.setReadOnlyAttribute(myProjectPom, true);

    // shouldn't throw 'File is read-only' exception.
    myProjectsManager.addDependency(myProjectsTree.findProject(myProjectPom),
                                    new MavenId("junit", "junit", "4.0"));
  }
}
