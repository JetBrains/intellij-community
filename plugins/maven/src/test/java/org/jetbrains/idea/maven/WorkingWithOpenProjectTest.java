package org.jetbrains.idea.maven;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.idea.maven.project.MavenImportProcessor;

import java.io.File;

public class WorkingWithOpenProjectTest extends ImportingTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  @Override
  protected void openProject() {
    ProjectManagerEx.getInstanceEx().openProject(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    ProjectManager.getInstance().closeProject(myProject);
    super.tearDown();
  }

  public void testShouldNotFailOnNewEmptyPomCreation() throws Exception {
    createModulePom("module", ""); // should not throw an exception
  }

  public void testShouldNotFailOnAddingNewContentRootWithAPomFile() throws Exception {
    File newRootDir = new File(dir, "newRoot");
    newRootDir.mkdirs();

    File pomFile = new File(newRootDir, "pom.xml");
    pomFile.createNewFile();

    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newRootDir);

    PsiTestUtil.addContentRoot(getModule("project"), root);  // should not throw an exception
  }
  
  public void testSavingAllDocumentBeforeSynchronization() throws Exception {
    Document d = FileDocumentManager.getInstance().getDocument(projectPom);
    d.setText(createValidPom("<groupId>test</groupId>" +
                             "<artifactId>project</artifactId>" +
                             "<version>1</version>" +

                             "<dependencies>" +
                             "  <dependency>" +
                             "    <groupId>junit</groupId>" +
                             "    <artifactId>junit</artifactId>" +
                             "    <version>4.0</version>" +
                             "  </dependency>" +
                             "</dependencies>"));
    
    new MavenImportProcessor(myProject).synchronize();

    assertModuleLibDep("project", "junit:junit:4.0");
  }
}
