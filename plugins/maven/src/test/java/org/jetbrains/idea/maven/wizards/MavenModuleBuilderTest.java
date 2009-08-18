package org.jetbrains.idea.maven.wizards;

import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.indices.ArchetypeInfo;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenId;

import java.util.List;

public class MavenModuleBuilderTest extends MavenImportingTestCase {
  private MavenModuleBuilder myBuilder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBuilder = new MavenModuleBuilder();

    createJdk("Java 1.5");
    setModuleNameAndRoot("module", getProjectPath());
  }

  public void testCreatingBlank() throws Exception {
    MavenId id = new MavenId("org.foo", "module", "1.0");
    createNewModule(id);

    List<MavenProject> projects = MavenProjectsManager.getInstance(myProject).getProjects();
    assertEquals(1, projects.size());

    MavenProject project = projects.get(0);
    assertEquals(id, project.getMavenId());

    assertModules("module");
    MavenProjectsManager.getInstance(myProject).isMavenizedModule(getModule("module"));
    assertSame(project, MavenProjectsManager.getInstance(myProject).findProject(getModule("module")));

    assertNotNull(myProjectRoot.findFileByRelativePath("src/main/java"));
    assertNotNull(myProjectRoot.findFileByRelativePath("src/test/java"));

    assertSources("module", "src/main/java");
    assertTestSources("module", "src/test/java");
  }

  public void testInheritJdkFromProject() throws Exception {
    createNewModule(new MavenId("org.foo", "module", "1.0"));
    ModuleRootManager manager = ModuleRootManager.getInstance(getModule("module"));
    assertTrue(manager.isSdkInherited());
  }

  public void testCreatingFromArchetype() throws Exception {
    setArchetype(new ArchetypeInfo("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null));
    MavenId id = new MavenId("org.foo", "module", "1.0");
    createNewModule(id);

    List<MavenProject> projects = MavenProjectsManager.getInstance(myProject).getProjects();
    assertEquals(1, projects.size());

    MavenProject project = projects.get(0);
    assertEquals(id, project.getMavenId());

    assertNotNull(myProjectRoot.findFileByRelativePath("src/main/java/org/foo/App.java"));
    assertNotNull(myProjectRoot.findFileByRelativePath("src/test/java/org/foo/AppTest.java"));

    assertSources("module", "src/main/java");
    assertTestSources("module", "src/test/java");
  }

  public void testAddingNewlyCreatedModuleToTheAggregator() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals(createPomXml("<groupId>test</groupId>" +
                                  "<artifactId>project</artifactId>\r\n" +
                                  "    <packaging>pom</packaging>\r\n" +
                                  "    <version>1</version>\r\n" +
                                  "    <modules>\r\n" +
                                  "        <module>module</module>\r\n" +
                                  "    </modules>\r\n"),
                 VfsUtil.loadText(myProjectPom));
  }

  public void testAddingManagedProjectIfNoArrgerator() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(null);
    createNewModule(new MavenId("org.foo", "module", "1.0"));
    myProjectRoot.findFileByRelativePath("module/pom.xml");

    assertEquals(2, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());
  }

  public void testDoNotAddManagedProjectIfAddingAsModuleToAggregator() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setAggregatorProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));
    myProjectRoot.findFileByRelativePath("module/pom.xml");

    assertEquals(1, myProjectsManager.getProjectsTreeForTests().getManagedFilesPaths().size());
  }

  public void testAddingParent() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\r\n" +
                 "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\r\n" +
                 "    <parent>\r\n" +
                 "        <artifactId>project</artifactId>\r\n" +
                 "        <groupId>test</groupId>\r\n" +
                 "        <version>1</version>\r\n" +
                 "    </parent>\r\n" +
                 "    <modelVersion>4.0.0</modelVersion>\r\n" +
                 "\r\n" +
                 "    <groupId>org.foo</groupId>\r\n" +
                 "    <artifactId>module</artifactId>\r\n" +
                 "    <version>1.0</version>\r\n" +
                 "\r\n" +
                 "\r\n" +
                 "</project>",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  public void testAddingParentWithInheritedProperties() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    setInheritedOptions(true, true);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\r\n" +
                 "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\r\n" +
                 "    <parent>\r\n" +
                 "        <artifactId>project</artifactId>\r\n" +
                 "        <groupId>test</groupId>\r\n" +
                 "        <version>1</version>\r\n" +
                 "    </parent>\r\n" +
                 "    <modelVersion>4.0.0</modelVersion>\r\n" +
                 "\r\n" +
                 "    <artifactId>module</artifactId>\r\n" +
                 "\r\n" +
                 "\r\n" +
                 "</project>",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  public void testAddingParentAndInheritWhenGeneratingFromArchetype() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/module");
    setParentProject(myProjectPom);
    setInheritedOptions(true, true);
    setArchetype(new ArchetypeInfo("org.apache.maven.archetypes", "maven-archetype-quickstart", "1.0", null, null));
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
                 "    <parent>\n" +
                 "        <artifactId>project</artifactId>\n" +
                 "        <groupId>test</groupId>\n" +
                 "        <version>1</version>\n" +
                 "    </parent>\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "    <artifactId>module</artifactId>\n" +
                 "    <packaging>jar</packaging>\n" +
                 "    <name>module</name>\n" +
                 "    <url>http://maven.apache.org</url>\n" +
                 "    <dependencies>\n" +
                 "        <dependency>\n" +
                 "            <groupId>junit</groupId>\n" +
                 "            <artifactId>junit</artifactId>\n" +
                 "            <version>3.8.1</version>\n" +
                 "            <scope>test</scope>\n" +
                 "        </dependency>\n" +
                 "    </dependencies>\n" +
                 "</project>\n",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("module/pom.xml")));
  }

  public void testAddingParentWithRelativePath() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    setModuleNameAndRoot("module", getProjectPath() + "/subDir/module");
    setParentProject(myProjectPom);
    createNewModule(new MavenId("org.foo", "module", "1.0"));

    assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\r\n" +
                 "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\r\n" +
                 "    <parent>\r\n" +
                 "        <artifactId>project</artifactId>\r\n" +
                 "        <groupId>test</groupId>\r\n" +
                 "        <version>1</version>\r\n" +
                 "        <relativePath>../../pom.xml</relativePath>\r\n" +
                 "    </parent>\r\n" +
                 "    <modelVersion>4.0.0</modelVersion>\r\n" +
                 "\r\n" +
                 "    <groupId>org.foo</groupId>\r\n" +
                 "    <artifactId>module</artifactId>\r\n" +
                 "    <version>1.0</version>\r\n" +
                 "\r\n" +
                 "\r\n" +
                 "</project>",
                 VfsUtil.loadText(myProjectRoot.findFileByRelativePath("subDir/module/pom.xml")));
  }

  public void testFindingPotentialParentInNotMavenizedProject() throws Exception {
    Module module = createModule("project");
    VirtualFile dir = module.getModuleFile().getParent();
    dir.createChildData(this, "pom.xml");

    setModuleNameAndRoot("module", dir.getPath() + "/module");

     // should not throw
    MavenProject project = myBuilder.findPotentialParentProject(myProject);
    assertNull(project);
  }

  private void setModuleNameAndRoot(String name, String root) {
    myBuilder.setName(name);
    myBuilder.setModuleFilePath(root + "/" + name + ".iml");
    myBuilder.setContentEntryPath(root);
  }

  private void setAggregatorProject(VirtualFile pom) {
    myBuilder.setAggregatorProject(myProjectsManager.findProject(pom));
  }

  private void setParentProject(VirtualFile pom) {
    myBuilder.setParentProject(myProjectsManager.findProject(pom));
  }

  private void setInheritedOptions(boolean groupId, boolean version) {
    myBuilder.setInheritedOptions(groupId, version);
  }

  private void setArchetype(ArchetypeInfo archetype) {
    myBuilder.setArchetype(archetype);
  }

  private void createNewModule(MavenId id) throws Exception {
    myBuilder.setProjectId(id);

    ModifiableModuleModel model = ModuleManager.getInstance(myProject).getModifiableModel();
    myBuilder.createModule(model);
    model.commit();

    resolveDependenciesAndImport();
  }
}
