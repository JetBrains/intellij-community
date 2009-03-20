package org.jetbrains.idea.maven.compiler;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.MavenImportingTestCase;

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

  public void testIncludesAndExcludes() throws Exception {
    createProjectSubFile("res/dir/file.properties");
    createProjectSubFile("res/dir/file.xml");
    createProjectSubFile("res/file.properties");
    createProjectSubFile("res/file.xml");
    createProjectSubFile("res/file.txt");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <includes>" +
                  "        <include>**/*.properties</include>" +
                  "        <include>**/*.xml</include>" +
                  "      </includes>" +
                  "      <excludes>" +
                  "        <exclude>*.properties</exclude>" +
                  "        <exclude>dir/*.xml</exclude>" +
                  "      </excludes>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    compileModules("project");

    assertCopied("target/classes/dir/file.properties");
    assertNotCopied("target/classes/dir/file.xml");
    assertNotCopied("target/classes/file.properties");
    assertCopied("target/classes/file.xml");
    assertNotCopied("target/classes/file.txt");
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

    updateProjectPom("<groupId>test</groupId>" +
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

    updateProjectPom("<groupId>test</groupId>" +
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