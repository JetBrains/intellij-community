package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.dom.model.MavenModel;

import java.io.File;

public class PropertyResolverTest extends MavenImportingTestCase {
  public void testResolvingProjectAttributes() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals("test", resolve("${project.groupId}", myProjectPom));
    assertEquals("test", resolve("${pom.groupId}", myProjectPom));
  }

  public void testResolvingProjectParentAttributes() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +

                  "<parent>" +
                  "  <groupId>parent.test</groupId>" +
                  "  <artifactId>parent.project</artifactId>" +
                  "  <version>parent.1</version>" +
                  "</parent>");

    assertEquals("parent.test", resolve("${project.parent.groupId}", myProjectPom));
    assertEquals("parent.test", resolve("${pom.parent.groupId}", myProjectPom));
  }

  public void testResolvingAbsentProperties() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals("${project.parent.groupId}", resolve("${project.parent.groupId}", myProjectPom));
  }

  public void testResolvingProjectDirectories() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(new File(getProjectPath(), "target").getPath(),
                 resolve("${project.build.directory}", myProjectPom));
    assertEquals(new File(getProjectPath(), "src/main/java").getPath(),
                 resolve("${project.build.sourceDirectory}", myProjectPom));
  }

  public void testResolvingProjectAndParentProperties() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<properties>" +
                     " <parentProp>parent.value</parentProp>" +
                     "</properties>" +

                     "<modules>" +
                     "  <module>m</module>" +
                     "</modules>");

    VirtualFile f = createModulePom("m",
                                    "<groupId>test</groupId>" +
                                    "<artifactId>m</artifactId>" +
                                    "<version>1</version>" +

                                    "<properties>" +
                                    " <moduleProp>module.value</moduleProp>" +
                                    "</properties>" +

                                    "<parent>" +
                                    "  <groupId>test</groupId>" +
                                    "  <artifactId>project</artifactId>" +
                                    "  <version>1</version>" +
                                    "</parent>");

    importProject();

    assertEquals("parent.value", resolve("${parentProp}", f));
    assertEquals("module.value", resolve("${moduleProp}", f));

    assertEquals("parent.value", resolve("${project.parentProp}", f));
    assertEquals("parent.value", resolve("${pom.parentProp}", f));
    assertEquals("module.value", resolve("${project.moduleProp}", f));
    assertEquals("module.value", resolve("${pom.moduleProp}", f));
  }

  public void testResolvingBasedirProperties() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(getProjectPath(), resolve("${basedir}", myProjectPom));
    assertEquals(getProjectPath(), resolve("${project.basedir}", myProjectPom));
    assertEquals(getProjectPath(), resolve("${pom.basedir}", myProjectPom));
  }

  public void testResolvingSystemProperties() throws Exception {
    String javaHome = System.getProperty("java.home");
    String tempDir = System.getenv("TEMP");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals(javaHome, resolve("${java.home}", myProjectPom));
    assertEquals(tempDir, resolve("${env.TEMP}", myProjectPom));
  }

  public void testAllProperties() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals("foo test-project bar",
                 resolve("foo ${project.groupId}-${project.artifactId} bar", myProjectPom));
  }

  public void testIncompleteProperties() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    assertEquals("${project.groupId", resolve("${project.groupId", myProjectPom));
    assertEquals("$project.groupId}", resolve("$project.groupId}", myProjectPom));
    assertEquals("{project.groupId}", resolve("{project.groupId}", myProjectPom));
  }

  public void testUncomittedProperties() throws Exception {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    FileDocumentManager.getInstance().getDocument(myProjectPom).setText(
        createProjectXml("<groupId>test</groupId>" +
                         "<artifactId>project</artifactId>" +
                         "<version>1</version>" +

                         "<properties>" +
                         " <uncomitted>value</uncomitted>" +
                         "</properties>") );

    assertEquals("value", resolve("${uncomitted}", myProjectPom));
  }

  private String resolve(String text, VirtualFile f) {
    Document d = FileDocumentManager.getInstance().getDocument(f);
    PsiFile psi = PsiDocumentManager.getInstance(myProject).getPsiFile(d);

    DomManager domManager = DomManager.getDomManager(myProject);
    DomFileElement<MavenModel> dom = domManager.getFileElement((XmlFile)psi, MavenModel.class);

    return PropertyResolver.resolve(text, dom);
  }
}
