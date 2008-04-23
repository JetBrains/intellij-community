package org.jetbrains.idea.maven;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.idea.maven.project.MavenImportProcessor;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkingWithOpenProjectTest extends ImportingTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
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
    File newRootDir = new File(myDir, "newRoot");
    newRootDir.mkdirs();

    File pomFile = new File(newRootDir, "pom.xml");
    pomFile.createNewFile();

    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newRootDir);

    PsiTestUtil.addContentRoot(getModule("project"), root);  // should not throw an exception
  }
  
  public void testSavingAllDocumentBeforeSynchronization() throws Exception {
    Document d = FileDocumentManager.getInstance().getDocument(myProjectPom);
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
    
    MavenProjectsManager.getInstance(myProject).setOriginalFiles(Collections.singleton(myProjectPom));
    new MavenImportProcessor(myProject).synchronize(new ArrayList<Pair<File, List<String>>>());

    assertModuleLibDep("project", "junit:junit:4.0");
  }
}
