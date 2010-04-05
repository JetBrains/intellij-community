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
package org.jetbrains.idea.maven.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.importing.MavenDefaultModifiableModelsProvider;

public class ResourceCopyingTest extends MavenImportingTestCase {
  public void testBasic() throws Exception {
    createProjectSubFile("src/main/resources/dir/file.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
    compileModules("project");

    assertCopied("target/classes/dir/file.properties");
  }

  public void testCustomResources() throws Exception {
    createProjectSubFile("res/dir1/file1.properties");
    createProjectSubFile("testRes/dir2/file2.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource><directory>res</directory></resource>" +
                  "  </resources>" +
                  "  <testResources>" +
                  "    <testResource><directory>testRes</directory></testResource>" +
                  "  </testResources>" +
                  "</build>");

    compileModules("project");

    assertCopied("target/classes/dir1/file1.properties");
    assertCopied("target/test-classes/dir2/file2.properties");
  }

  public void testCustomTargetPath() throws Exception {
    createProjectSubFile("res/dir/file.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <targetPath>foo</targetPath>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertCopied("target/classes/foo/dir/file.properties");
  }

  public void testAbsoluteCustomTargetPath() throws Exception {
    createProjectSubFile("res/foo/file.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <targetPath>${build.directory}/anotherDir</targetPath>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertCopied("target/anotherDir/foo/file.properties");
  }

  public void testIncludesAndExcludes() throws Exception {
    createProjectSubFile("res/dir/file.xxx");
    createProjectSubFile("res/dir/file.yyy");
    createProjectSubFile("res/file.xxx");
    createProjectSubFile("res/file.yyy");
    createProjectSubFile("res/file.zzz");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <includes>" +
                  "        <include>**/*.xxx</include>" +
                  "        <include>**/*.yyy</include>" +
                  "      </includes>" +
                  "      <excludes>" +
                  "        <exclude>*.xxx</exclude>" +
                  "        <exclude>dir/*.yyy</exclude>" +
                  "      </excludes>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");

    assertCopied("target/classes/dir/file.xxx");
    assertNotCopied("target/classes/dir/file.yyy");
    assertNotCopied("target/classes/file.xxx");
    assertCopied("target/classes/file.yyy");
    assertNotCopied("target/classes/file.zzz");
  }

  public void testDoNotCopyIgnoredFiles() throws Exception {
    createProjectSubFile("res/CVS/file.properties");
    createProjectSubFile("res/.svn/file.properties");
    createProjectSubFile("res/zzz/file.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <includes>" +
                  "        <include>**/*.properties</include>" +
                  "      </includes>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");

    assertNotCopied("target/classes/CVS");
    assertNotCopied("target/classes/.svn");
    assertCopied("target/classes/zzz/file.properties");
  }

  public void testDeletingFilesThatWasCopiedAndThenDeleted() throws Exception {
    VirtualFile file = createProjectSubFile("res/file.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertCopied("target/classes/file.properties");

    file.delete(this);

    compileModules("project");
    assertNotCopied("target/classes/file.properties");
  }

  public void testDeletingFilesThatWasCopiedAndThenExcluded() throws Exception {
    createProjectSubFile("res/file.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertCopied("target/classes/file.properties");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<build>" +
                     "  <resources>" +
                     "    <resource>" +
                     "      <directory>res</directory>" +
                     "      <excludes>" +
                     "        <exclude>**/*</exclude>" +
                     "      </excludes>" +
                     "    </resource>" +
                     "  </resources>" +
                     "</build>");
    importProject();

    compileModules("project");
    assertNotCopied("target/classes/file.properties");
  }

  public void testDoNotCopyExcludedStandardResources() throws Exception {
    if (ignore()) return;
    
    CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    configuration.addResourceFilePattern("*.zzz");

    createProjectSubFile("res/file.xxx");
    createProjectSubFile("res/file.zzz");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <includes>" +
                  "        <include>**/*.xxx</include>" +
                  "      </includes>" +
                  "      <excludes>" +
                  "        <exclude>**/*.zzz</exclude>" +
                  "      </excludes>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertCopied("target/classes/file.xxx");
    assertNotCopied("target/classes/file.zzz");
  }

  public void testDeletingManuallyCopyedFiles() throws Exception {
    if (ignore()) return;

    createProjectSubFile("res/file.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertCopied("target/classes/file.properties");
    createProjectSubFile("target/classes/file2.properties");

    compileModules("project");
    assertCopied("target/classes/file.properties");
    assertNotCopied("target/classes/file2.properties");
  }

  public void testDeletingFilesCopyiedByIdeaCompiler() throws Exception {
    if (ignore()) return;

    createProjectSubFile("res/file.properties");
    createProjectSubFile("res/file.xml");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <includes>" +
                  "        <include>**/*.properties</include>" +
                  "      </includes>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertCopied("target/classes/file.properties");
    assertNotCopied("target/classes/file.xml");
  }

  public void testCopyManuallyDeletedFiles() throws Exception {
    createProjectSubFile("res/file.properties");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");
    assertCopied("target/classes/file.properties");
    myProjectPom.getParent().findFileByRelativePath("target").delete(this);

    compileModules("project");
    assertCopied("target/classes/file.properties");
  }

  public void testWorkCorrectlyIfFoldersMarkedAsSource() throws Exception {
    createProjectSubFile("src/main/resources/file.properties");
    createProjectSubFile("src/main/resources/file.txt");
    createProjectSubFile("src/main/resources/file.xxx");
    createProjectSubFile("src/main/ideaRes/file2.xxx");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    MavenRootModelAdapter adapter = new MavenRootModelAdapter(myProjectsTree.findProject(myProjectPom),
                                                              getModule("project"),
                                                              new MavenDefaultModifiableModelsProvider(myProject));
    adapter.addSourceFolder(myProjectRoot.findFileByRelativePath("src/main/resources").getPath(), false);
    adapter.addSourceFolder(myProjectRoot.findFileByRelativePath("src/main/ideaRes").getPath(), false);
    adapter.getRootModel().commit();

    assertSources("project", "src/main/resources", "src/main/ideaRes");

    compileModules("project");

    assertCopied("target/classes/file.properties");
    assertCopied("target/classes/file.txt");
    assertCopied("target/classes/file.xxx");
    assertNotCopied("target/classes/file2.xxx");
  }

  public void testDoNotDeleteFilesFromOtherModulesOutput() throws Exception {
    createProjectSubFile("m1/resources/file.xxx");
    createProjectSubFile("m2/resources/file.yyy");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    "  <resources>" +
                    "    <resource>" +
                    "      <directory>resources</directory>" +
                    "    </resource>" +
                    "  </resources>" +
                    "</build>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    "  <resources>" +
                    "    <resource>" +
                    "      <directory>resources</directory>" +
                    "    </resource>" +
                    "  </resources>" +
                    "</build>");
    importProject();

    compileModules("project", "m1", "m2");
    assertCopied("m1/target/classes/file.xxx");
    assertCopied("m2/target/classes/file.yyy");

    compileModules("m1");
    assertCopied("m1/target/classes/file.xxx");
    assertCopied("m2/target/classes/file.yyy");

    compileModules("m2");
    assertCopied("m1/target/classes/file.xxx");
    assertCopied("m2/target/classes/file.yyy");

    compileModules("project");
    assertCopied("m1/target/classes/file.xxx");
    assertCopied("m2/target/classes/file.yyy");
  }

  public void testDoNotDeleteFilesFromOtherModulesOutputWhenOutputIsTheSame() throws Exception {
    createProjectSubFile("resources/file.xxx");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<modules>" +
                     "  <module>m1</module>" +
                     "  <module>m2</module>" +
                     "</modules>");

    createModulePom("m1",
                    "<groupId>test</groupId>" +
                    "<artifactId>m1</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    "  <resources>" +
                    "    <resource>" +
                    "      <directory>../resources</directory>" +
                    "    </resource>" +
                    "  </resources>" +
                    "</build>");

    createModulePom("m2",
                    "<groupId>test</groupId>" +
                    "<artifactId>m2</artifactId>" +
                    "<version>1</version>" +

                    "<build>" +
                    "  <resources>" +
                    "    <resource>" +
                    "      <directory>../resources</directory>" +
                    "    </resource>" +
                    "  </resources>" +
                    "</build>");
    importProject();

    setModulesOutput(myProjectRoot.createChildDirectory(this, "output"), "project", "m1", "m2");

    compileModules("project", "m1", "m2");
    assertCopied("output/file.xxx");

    compileModules("m1");
    assertCopied("output/file.xxx");

    compileModules("m2");
    assertCopied("output/file.xxx");

    compileModules("project");
    assertCopied("output/file.xxx");
  }

  private void setModulesOutput(VirtualFile output, String... moduleNames) {
    for (String each : moduleNames) {
      ModifiableRootModel model = ModuleRootManager.getInstance(getModule(each)).getModifiableModel();
      model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPath(output);
      model.getModuleExtension(CompilerModuleExtension.class).setCompilerOutputPathForTests(output);
      model.commit();
    }
  }

  public void testWebResources() throws Exception {
    if (ignore()) return;

    createProjectSubFile("res/dir/file.properties");
    createProjectSubFile("res/dir/file.xml");
    createProjectSubFile("res/file.properties");
    createProjectSubFile("res/file.xml");
    createProjectSubFile("res/file.txt");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<packaging>war</packaging>" +

                  "<build>" +
                  "  <plugins>" +
                  "    <plugin>" +
                  "      <groupId>org.apache.maven.plugins</groupId>" +
                  "      <artifactId>maven-war-plugin</artifactId>" +
                  "      <configuration>" +
                  "        <webResources>" +
                  "          <directory>res</directory>" +
                  "          <includes>" +
                  "            <include>**/*.properties</include>" +
                  "            <include>**/*.xml</include>" +
                  "          </includes>" +
                  "          <excludes>" +
                  "            <exclude>*.properties</exclude>" +
                  "            <exclude>dir/*.xml</exclude>" +
                  "          </excludes>" +
                  "        </webResources>" +
                  "      </configuration>" +
                  "    </plugin>" +
                  "  </plugins>" +
                  "</build>");

    compileModules("project");

    assertCopied("target/classes/dir/file.properties");
    assertNotCopied("target/classes/dir/file.xml");
    assertNotCopied("target/classes/file.properties");
    assertCopied("target/classes/file.xml");
    assertNotCopied("target/classes/file.txt");
  }

  public void testOverridingWebResourceFilters() throws Exception {
    if (ignore()) return;

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +
                     "<packaging>war</packaging>" +

                     "<build>" +
                     "  <plugins>" +
                     "    <plugin>" +
                     "      <groupId>org.apache.maven.plugins</groupId>" +
                     "      <artifactId>maven-war-plugin</artifactId>" +
                     "      <configuration>\n" +
                     "        <!-- the default value is the filter list under build -->\n" +
                     "        <!-- specifying a filter will override the filter list under build -->\n" +
                     "        <filters>\n" +
                     "          <filter>properties/config.prop</filter>\n" +
                     "        </filters>\n" +
                     "        <nonFilteredFileExtensions>\n" +
                     "          <!-- default value contains jpg,jpeg,gif,bmp,png -->\n" +
                     "          <nonFilteredFileExtension>pdf</nonFilteredFileExtensions>\n" +
                     "        </nonFilteredFileExtensions>\n" +
                     "        <webResources>\n" +
                     "          <resource>\n" +
                     "            <directory>resource2</directory>\n" +
                     "            <!-- it's not a good idea to filter binary files -->\n" +
                     "            <filtering>false</filtering>\n" +
                     "          </resource>\n" +
                     "          <resource>\n" +
                     "            <directory>configurations</directory>\n" +
                     "            <!-- enable filtering -->\n" +
                     "            <filtering>true</filtering>\n" +
                     "            <excludes>\n" +
                     "              <exclude>**/properties</exclude>\n" +
                     "            </excludes>\n" +
                     "          </resource>\n" +
                     "        </webResources>\n" +
                     "      </configuration>" +
                     "    </plugin>" +
                     "  </plugins>" +
                     "</build>");
  }

  private void assertCopied(String path) {
    assertNotNull(myProjectPom.getParent().findFileByRelativePath(path));
  }

  private void assertNotCopied(String path) {
    assertNull(myProjectPom.getParent().findFileByRelativePath(path));
  }
}