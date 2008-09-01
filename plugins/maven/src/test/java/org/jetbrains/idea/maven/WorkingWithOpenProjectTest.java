package org.jetbrains.idea.maven;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;

public class WorkingWithOpenProjectTest extends MavenImportingTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  @Override
  protected void tearDown() throws Exception {
    //ProjectManager.getInstance().closeProject(myProject);
    super.tearDown();
  }

  public void testShouldNotFailOnNewEmptyPomCreation() throws Exception {
    createModulePom("module", ""); // should not throw an exception
  }

  public void testShouldNotFailOnAddingNewContentRootWithAPomFile() throws Exception {
    File newRootDir = new File(myDir, "newRoot");
    newRootDir.mkdirs();

    File pomFile = new File(newRootDir, "pom.xml");
    pomFile.createNewFile();

    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newRootDir);

    PsiTestUtil.addContentRoot(getModule("project"), root);  // should not throw an exception
  }
  
  public void testSavingAllDocumentBeforeReimport() throws Exception {
    Document d = FileDocumentManager.getInstance().getDocument(myProjectPom);
    d.setText(createProjectXml("<groupId>test</groupId>" +
                             "<artifactId>project</artifactId>" +
                             "<version>1</version>" +

                             "<dependencies>" +
                             "  <dependency>" +
                             "    <groupId>junit</groupId>" +
                             "    <artifactId>junit</artifactId>" +
                             "    <version>4.0</version>" +
                             "  </dependency>" +
                             "</dependencies>"));
    
    myMavenProjectsManager.doReimport();

    assertModuleLibDep("project", "Maven: junit:junit:4.0");
  }
}
