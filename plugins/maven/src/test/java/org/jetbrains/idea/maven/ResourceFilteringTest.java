package org.jetbrains.idea.maven;

import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
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

    assertResult("target/classes/file.properties", "value=1");
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

    assertResult("target/test-classes/file.properties", "value=1");
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

    assertResult("target/classes/file1.properties", "value=1");
    assertResult("target/classes/file2.properties", "value=1");
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

    assertResult(m1, "target/classes/file1.properties", "value=1");
    assertResult(m2, "target/classes/file2.properties", "value=2");
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

    assertResult("target/classes/file1.properties", "value=1");
    assertResult("target/classes/file2.properties", "value=${project.version}");
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

    assertResult("target/classes/file.properties", "value=${foo.bar}");
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
    assertResult("target/classes/file.properties", "value=1");

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

    assertResult("target/classes/file.properties", "value=${project.version}");
  }

  public void testSameFileInSourcesAndTestSources() throws Exception {
    createProjectSubFile("src/main/resources/file.properties").setBinaryContent("foo=${foo.main}".getBytes());
    createProjectSubFile("src/test/resources/file.properties").setBinaryContent("foo=${foo.test}".getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<properties>" +
                  "  <foo.main>main</foo.main>" +
                  "  <foo.test>test</foo.test>" +
                  "</properties>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>src/main/resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "  <testResources>" +
                  "    <testResource>" +
                  "      <directory>src/test/resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </testResource>" +
                  "  </testResources>" +
                  "</build>");
    compileModules("project");

    assertResult("target/classes/file.properties", "foo=main");
    assertResult("target/test-classes/file.properties", "foo=test");
  }

  public void testCustomFilters() throws Exception {
    createProjectSubFile("filters/filter1.properties").setBinaryContent(
      ("xxx=value\n" +
       "yyy=${project.version}\n").getBytes());
    createProjectSubFile("filters/filter2.properties").setBinaryContent(
      ("zzz=value2").getBytes());
    createProjectSubFile("resources/file.properties").setBinaryContent(
      ("value1=${xxx}\n" +
       "value2=${yyy}\n" +
       "value3=${zzz}\n").getBytes());

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <filters>" +
                  "    <filter>filters/filter1.properties</filter>" +
                  "    <filter>filters/filter2.properties</filter>" +
                  "  </filters>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>resources</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");
    assertSources("project", "resources");
    compileModules("project");

    assertResult("target/classes/file.properties", "value1=value\n" +
                                                   "value2=1\n" +
                                                   "value3=value2\n");
  }

  private void assertResult(String relativePath, String content) throws IOException {
    assertResult(myProjectPom, relativePath, content);
  }

  private void assertResult(VirtualFile pomFile, String relativePath, String content) throws IOException {
    VirtualFile file = pomFile.getParent().findFileByRelativePath(relativePath);
    assertNotNull(file);
    assertEquals(content, VfsUtil.loadText(file));
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
