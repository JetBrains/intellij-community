package org.jetbrains.idea.maven;

import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;
import java.util.List;

public class ResourceFilteringTest extends MavenImportingTestCase {
  public void testBasic() throws Exception {
    createProjectSubFile("resources/file.properties").setBinaryContent("value=${project.version}".getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    assertSources("project", "resources");
    compileModules("project");

    VirtualFile result = LocalFileSystem.getInstance().findFileByPath(getProjectPath() + "/target/classes/file.properties");
    assertNotNull(result);
    assertEquals("value=1", VfsUtil.loadText(result));
  }

  public void testFilteringTestResources() throws Exception {
    createProjectSubFile("resources/file.properties").setBinaryContent("value=${project.version}".getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <testResources>" +
                  "    <testResource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </testResource>" +
                  "  </testResources>" +
                  "</build>");
    assertTestSources("project", "resources");
    compileModules("project");

    VirtualFile result = LocalFileSystem.getInstance().findFileByPath(getProjectPath() + "/target/test-classes/file.properties");
    assertNotNull(result);
    assertEquals("value=1", VfsUtil.loadText(result));
  }

  public void testFilterWithSeveralResourceFolders() throws Exception {
    createProjectSubFile("resources1/file1.properties").setBinaryContent("value=${project.version}".getBytes());
    createProjectSubFile("resources2/file2.properties").setBinaryContent("value=${project.version}".getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources1</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources2</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    assertSources("project", "resources1", "resources2");
    compileModules("project");


    VirtualFile file1 = myProjectPom.getParent().findFileByRelativePath("target/classes/file1.properties");
    VirtualFile file2 = myProjectPom.getParent().findFileByRelativePath("target/classes/file2.properties");
    assertNotNull(file1);
    assertNotNull(file2);

    assertEquals("value=1", VfsUtil.loadText(file1));
    assertEquals("value=1", VfsUtil.loadText(file2));
  }

  public void testFilterWithSeveralModules() throws Exception {
    createProjectSubFile("module1/resources/file1.properties").setBinaryContent("value=${project.version}".getBytes());
    createProjectSubFile("module2/resources/file2.properties").setBinaryContent("value=${project.version}".getBytes());

    VirtualFile m1 = createModulePom("module1",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>module1</artifactId>" +
                                     "<version>1</version>" +

                                     "<build>" +
                                     "  <resources>" +
                                     "    <resource>" +
                                     "      <directory>resources</directory>" +
                                     "      <filtering>true</filtering>" +
                                     "    </resource>" +
                                     "  </resources>" +
                                     "</build>");

    VirtualFile m2 = createModulePom("module2",
                                     "<groupId>test</groupId>" +
                                     "<artifactId>module2</artifactId>" +
                                     "<version>2</version>" +

                                     "<build>" +
                                     "  <resources>" +
                                     "    <resource>" +
                                     "      <directory>resources</directory>" +
                                     "      <filtering>true</filtering>" +
                                     "    </resource>" +
                                     "  </resources>" +
                                     "</build>");

    importSeveralProjects(m1, m2);
    assertSources("module1", "resources");
    assertSources("module2", "resources");
    compileModules("module1", "module2");

    VirtualFile file1 = m1.getParent().findFileByRelativePath("target/classes/file1.properties");
    VirtualFile file2 = m2.getParent().findFileByRelativePath("target/classes/file2.properties");
    assertNotNull(file1);
    assertNotNull(file2);

    assertEquals("value=1", VfsUtil.loadText(file1));
    assertEquals("value=2", VfsUtil.loadText(file2));
  }

  public void testDoNotFilterIfNotRequested() throws Exception {
    createProjectSubFile("resources1/file1.properties").setBinaryContent("value=${project.version}".getBytes());
    createProjectSubFile("resources2/file2.properties").setBinaryContent("value=${project.version}".getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources1</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "    <resource>" +
                  "      <directory>resources2</directory>" +
                  "      <filtering>false</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    assertSources("project", "resources1", "resources2");
    compileModules("project");


    VirtualFile file1 = myProjectPom.getParent().findFileByRelativePath("target/classes/file1.properties");
    VirtualFile file2 = myProjectPom.getParent().findFileByRelativePath("target/classes/file2.properties");
    assertNotNull(file1);
    assertNotNull(file2);

    assertEquals("value=1", VfsUtil.loadText(file1));
    assertEquals("value=${project.version}", VfsUtil.loadText(file2));
  }

  public void testDoNotChangeFileIfPropertyIsNotResolved() throws Exception {
    createProjectSubFile("resources/file.properties").setBinaryContent("value=${foo.bar}".getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    assertSources("project", "resources");
    compileModules("project");

    VirtualFile result = LocalFileSystem.getInstance().findFileByPath(getProjectPath() + "/target/classes/file.properties");
    assertNotNull(result);
    assertEquals("value=${foo.bar}", VfsUtil.loadText(result));
  }

  public void testChangingResolvedPropsBackWhenSettingsIsChange() throws Exception {
    createProjectSubFile("resources/file.properties").setBinaryContent("value=${project.version}".getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    compileModules("project");

    VirtualFile result = LocalFileSystem.getInstance().findFileByPath(getProjectPath() + "/target/classes/file.properties");
    assertNotNull(result);
    assertEquals("value=1", VfsUtil.loadText(result));

    updateProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>resources</directory>" +
                     "      <filtering>false</filtering>" +
                     "    </resource>" +
                     "  </resources>" +
                     "</build>");
    importProject();
    compileModules("project");

    result = LocalFileSystem.getInstance().findFileByPath(getProjectPath() + "/target/classes/file.properties");
    assertNotNull(result);
    assertEquals("value=${project.version}", VfsUtil.loadText(result));
  }

  private void compileModules(String... modules) {
    for (String each : modules) {
      ModifiableRootModel model = ModuleRootManager.getInstance(getModule(each)).getModifiableModel();
      model.setSdk(JavaSdkImpl.getMockJdk15("java 1.5"));
      model.commit();
    }

    List<VirtualFile> roots = Arrays.asList(ProjectRootManager.getInstance(myProject).getContentSourceRoots());
    TranslatingCompilerFilesMonitor.getInstance().scanSourceContent(myProject, roots, roots.size(), true);

    CompilerManager.getInstance(myProject).make(new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        assertFalse(aborted);
        assertEquals(0, errors);
        assertEquals(0, warnings);
      }
    });
  }
}
