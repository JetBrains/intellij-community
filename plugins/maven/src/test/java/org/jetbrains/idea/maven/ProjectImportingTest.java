package org.jetbrains.idea.maven;/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.navigator.PomTreeStructure;
import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;
import org.jetbrains.idea.maven.project.MavenImportProcessor;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ProjectImportingTest extends IdeaTestCase {
  private VirtualFile root;
  private VirtualFile projectPom;
  private List<VirtualFile> poms = new ArrayList<VirtualFile>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          File dir = createTempDirectory();
          root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  protected void setUpModule() {
  }

  public void testSimpleProject() throws IOException {
    importProject("<?xml version=\"1.0\"?>\n" +
                  "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                  "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                  "\n" +
                  "  <modelVersion>4.0.0</modelVersion>\n" +
                  "  <groupId>mvn</groupId>\n" +
                  "  <artifactId>project</artifactId>\n" +
                  "  <packaging>jar</packaging>\n" +
                  "  <version>1</version>\n" +
                  "\n" +
                  "</project>");

    assertModules("project");
  }

  public void testProjectWithDependency() throws IOException {
    importProject("<?xml version=\"1.0\"?>\n" +
                  "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                  "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                  "\n" +
                  "  <modelVersion>4.0.0</modelVersion>\n" +
                  "  <groupId>mvn</groupId>\n" +
                  "  <artifactId>project</artifactId>\n" +
                  "  <packaging>jar</packaging>\n" +
                  "  <version>1</version>\n" +
                  "\n" +
                  "  <dependencies>\n" +
                  "    <dependency>\n" +
                  "      <groupId>junit</groupId>\n" +
                  "      <artifactId>junit</artifactId>\n" +
                  "      <version>4.0</version>\n" +
                  "    </dependency>\n" +
                  "  </dependencies>\n" +
                  "  \n" +
                  "</project>");

    assertModules("project");
  }

  public void testProjectWithProperty() throws IOException {
    importProject("<?xml version=\"1.0\"?>\n" +
                  "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                  "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                  "\n" +
                  "  <modelVersion>4.0.0</modelVersion>\n" +
                  "  <groupId>mvn</groupId>\n" +
                  "  <artifactId>project</artifactId>\n" +
                  "  <packaging>jar</packaging>\n" +
                  "  <version>1</version>\n" +
                  "\n" +
                  "  <dependencies>\n" +
                  "    <dependency>\n" +
                  "      <groupId>direct-system-dependency</groupId>\n" +
                  "      <artifactId>direct-system-dependency</artifactId>\n" +
                  "      <version>1.0</version>\n" +
                  "      <scope>system</scope>\n" +
                  "      <systemPath>${java.home}/lib/tools.jar</systemPath>" +
                  "    </dependency>\n" +
                  "  </dependencies>\n" +
                  "  \n" +
                  "</project>");

    assertModules("project");
  }

  public void testProjectWithEnvProperty() throws IOException {
    importProject("<?xml version=\"1.0\"?>\n" +
                  "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                  "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                  "\n" +
                  "  <modelVersion>4.0.0</modelVersion>\n" +
                  "  <groupId>mvn</groupId>\n" +
                  "  <artifactId>env-properties</artifactId>\n" +
                  "  <packaging>jar</packaging>\n" +
                  "  <version>1</version>\n" +
                  "\n" +
                  "  <dependencies>\n" +
                  "    <dependency>\n" +
                  "      <groupId>direct-system-dependency</groupId>\n" +
                  "      <artifactId>direct-system-dependency</artifactId>\n" +
                  "      <version>1.0</version>\n" +
                  "      <scope>system</scope>\n" +
                  "      <systemPath>${env.JAVA_HOME}/lib/tools.jar</systemPath>" +
                  "    </dependency>\n" +
                  "  </dependencies>\n" +
                  "  \n" +
                  "</project>");

    // This should fail when embedder will be able to handle env.XXX properties
    assertModules();
  }

  public void testModulesWithSlashesRegularAndBack() throws IOException {
    createProjectPom("<?xml version=\"1.0\"?>\n" +
                     "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                     "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                     "\n" +
                     "  <modelVersion>4.0.0</modelVersion>\n" +
                     "  <groupId>mvn.modules-with-slashes</groupId>\n" +
                     "  <artifactId>project</artifactId>\n" +
                     "  <packaging>pom</packaging>\n" +
                     "  <version>1</version>\n" +
                     "\n" +
                     "    <modules>\n" +
                     "        <module>dir\\m1</module>\n" +
                     "        <module>dir/m2</module>\n" +
                     "    </modules>\n" +
                     "\n" +
                     "</project>");

    createModulePom("dir/m1", "<?xml version=\"1.0\"?>\n" +
                              "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                              "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                              "  \n" +
                              "  <modelVersion>4.0.0</modelVersion>\n" +
                              "  <groupId>mvn.modules-with-slashes</groupId>\n" +
                              "  <artifactId>m1</artifactId>\n" +
                              "  <version>1</version>\n" +
                              "\n" +
                              "</project>");

    createModulePom("dir/m2", "<?xml version=\"1.0\"?>\n" +
                              "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                              "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                              "  \n" +
                              "  <modelVersion>4.0.0</modelVersion>\n" +
                              "  <groupId>mvn.modules-with-slashes</groupId>\n" +
                              "  <artifactId>m2</artifactId>\n" +
                              "  <version>1</version>\n" +
                              "\n" +
                              "</project>");

    importProject();
    assertModules("project", "m1", "m2");

    PomTreeStructure.RootNode r = createMavenTree();

    assertEquals(1, r.pomNodes.size());
    assertEquals("project", r.pomNodes.get(0).mavenProject.getArtifactId());

    assertEquals(2, r.pomNodes.get(0).modulePomsNode.pomNodes.size());
  }

  private PomTreeStructure.RootNode createMavenTree() {
    PomTreeStructure s = new PomTreeStructure(myProject, myProject.getComponent(MavenProjectsState.class),
                                              myProject.getComponent(MavenRepository.class),
                                              myProject.getComponent(MavenEventsHandler.class)) {
      {
        for (VirtualFile pom : poms) {
          this.root.addUnder(new PomNode(pom));
        }
      }

      protected PomTreeViewSettings getTreeViewSettings() {
        return new PomTreeViewSettings();
      }

      protected void updateTreeFrom(@Nullable SimpleNode node) {
      }
    };
    return (PomTreeStructure.RootNode)s.getRootElement();
  }

  private void assertModules(String... names) {
    Module[] actual = ModuleManager.getInstance(myProject).getModules();
    assertEquals(names.length, actual.length);
    for (Module m : actual) {
      assertTrue(Arrays.asList(names).contains(m.getName()));
    }
  }

  private void createProjectPom(String xml) throws IOException {
    projectPom = createPomFile(root, xml);
  }

  private void createModulePom(String relativePath, String xml) throws IOException {
    File externalDir = new File(root.getPath(), relativePath);
    externalDir.mkdirs();

    VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(externalDir);
    createPomFile(dir, xml);
  }

  private VirtualFile createPomFile(VirtualFile dir, String xml) throws IOException {
    VirtualFile f = dir.createChildData(null, "pom.xml");
    f.setBinaryContent(xml.getBytes());
    poms.add(f);
    return f;
  }

  private void importProject(String xml) throws IOException {
    createProjectPom(xml);
    importProject();
  }

  private void importProject() {
    ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    files.add(projectPom);
    ArrayList<String> profiles = new ArrayList<String>();

    MavenImportProcessor p = new MavenImportProcessor(myProject);
    p.createMavenProjectModel(new HashMap<VirtualFile, Module>(), files, profiles);
    p.createMavenToIdeaMapping(false);
    p.resolve(myProject, profiles);
    p.commit(myProject, profiles, false);
  }
}
