package org.jetbrains.idea.maven.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.project.MavenProject;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenFoldersConfiguratorTest extends MavenImportingTestCase {
  public void testUpdatingExternallyCreatedFolders() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myProjectRoot.getChildren(); // make sure fs is cached

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    new File(myProjectRoot.getPath(), "target/generated-sources/xxx").mkdirs();
    updateFolders();

    assertExcludes("project", "target/foo");
    assertSources("project", "target/generated-sources/xxx");
    
    assertNull(myProjectRoot.findChild("target"));
  }

  public void testUpdatingFoldersForAllTheProjects() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>");

    importProject();

    assertExcludes("m1", "target");
    assertExcludes("m2", "target");

    new File(myProjectRoot.getPath(), "m1/target/foo").mkdirs();
    new File(myProjectRoot.getPath(), "m1/target/generated-sources/xxx").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/bar").mkdirs();
    new File(myProjectRoot.getPath(), "m2/target/generated-sources/yyy").mkdirs();

    updateFolders();

    assertExcludes("m1", "target/foo");
    assertSources("m1", "target/generated-sources/xxx");

    assertExcludes("m2", "target/bar");
    assertSources("m2", "target/generated-sources/yyy");
  }

  public void testDoesNotTouchSourceFolders() throws Exception {
    createStdProjectFolders();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertSources("project", "src/main/java", "src/main/resources");
    assertTestSources("project", "src/test/java", "src/test/resources");

    updateFolders();

    assertSources("project", "src/main/java", "src/main/resources");
    assertTestSources("project", "src/test/java", "src/test/resources");
  }

  public void testDoesNotExcludeRegisteredSources() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    new File(myProjectRoot.getPath(), "target/foo").mkdirs();
    File sourceDir = new File(myProjectRoot.getPath(), "target/src");
    sourceDir.mkdirs();

    RootModelAdapter adapter = new RootModelAdapter(getModule("project"));
    adapter.addSourceFolder(sourceDir.getPath(), false);
    adapter.getRootModel().commit();

    updateFolders();

    assertSources("project", "target/src");
    assertExcludes("project", "target/foo");
  }

  public void testDoesNothingWithNonMavenModules() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createModule("userModule");
    updateFolders(); // shouldn't throw exceptions
  }

  public void testDoNotUpdateOutputFolders() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    RootModelAdapter adapter = new RootModelAdapter(getModule("project"));
    adapter.useModuleOutput(new File(myProjectRoot.getPath(), "target/my-classes").getPath(),
                            new File(myProjectRoot.getPath(), "target/my-test-classes").getPath());
    adapter.getRootModel().commit();
    
    updateFolders();

    ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule("project"));
    CompilerModuleExtension compiler = rootManager.getModuleExtension(CompilerModuleExtension.class);
    assertTrue(compiler.getCompilerOutputUrl(), compiler.getCompilerOutputUrl().endsWith("my-classes"));
    assertTrue(compiler.getCompilerOutputUrlForTests(), compiler.getCompilerOutputUrlForTests().endsWith("my-test-classes"));
  }

  public void testDoNotCommitIfFoldersWasNotChanged() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    final int[] count = new int[]{0};
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        count[0]++;
      }
    });

    updateFolders();

    assertEquals(0, count[0]);
  }

  public void testCommitOnlyOnceForAllModules() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<packaging>pom</packaging>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    VirtualFile m1 = createModulePom("m1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m1</artifactId>" +
                                     "<version>1</version>");

    VirtualFile m2 = createModulePom("m2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>m2</artifactId>" +
                                     "<version>1</version>");

    importProject();

    final int[] count = new int[]{0};
    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        count[0]++;
      }
    });

    new File(myProjectRoot.getPath(), "target/generated-sources/foo").mkdirs();
    new File(m1.getPath(), "target/generated-sources/bar").mkdirs();
    new File(m2.getPath(), "target/generated-sources/baz").mkdirs();

    updateFolders();

    assertEquals(1, count[0]);
  }

  private void updateFolders() throws MavenException {
    List<MavenProject> mavenProjects = new ArrayList<MavenProject>();
    for (MavenProjectModel each : myMavenTree.getProjects()) {
      mavenProjects.add(each.getMavenProject());
    }
    MavenFoldersConfigurator.updateProjectFolders(myProject, mavenProjects);
  }
}
