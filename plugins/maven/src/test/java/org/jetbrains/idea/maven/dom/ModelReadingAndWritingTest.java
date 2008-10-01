package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.idea.maven.MavenImportingTestCase;
import org.jetbrains.idea.maven.dom.model.Dependency;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.utils.MavenUtil;

public class ModelReadingAndWritingTest extends MavenImportingTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  public void testReading() throws Exception {
    MavenModel model = getDomModel();

    assertEquals("test", model.getGroupId().getStringValue());
    assertEquals("project", model.getArtifactId().getStringValue());
    assertEquals("1", model.getVersion().getStringValue());
  }

  public void testWriting() throws Exception {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        MavenModel model = getDomModel();

        model.getGroupId().setStringValue("foo");
        model.getArtifactId().setStringValue("bar");
        model.getVersion().setStringValue("baz");

        formatAndSaveProjectPomDocument();
      }
    }, null, null);

    assertEquals("<?xml version=\"1.0\"?>\r\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\r\n" +
                 "    <modelVersion>4.0.0</modelVersion>\r\n" +
                 "    <groupId>foo</groupId>\r\n" +
                 "    <artifactId>bar</artifactId>\r\n" +
                 "    <version>baz</version>\r\n" +
                 "</project>",
                 VfsUtil.loadText(myProjectPom));
  }

  public void testAddingADependency() throws Exception {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        MavenModel model = getDomModel();

        Dependency d = model.getDependencies().addDependency();
        d.getGroupId().setStringValue("group");
        d.getArtifactId().setStringValue("artifact");
        d.getVersion().setStringValue("version");

        formatAndSaveProjectPomDocument();
      }
    }, null, null);

    assertEquals("<?xml version=\"1.0\"?>\r\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\r\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\r\n" +
                 "    <modelVersion>4.0.0</modelVersion>\r\n" +
                 "    <groupId>test</groupId>\r\n" +
                 "    <artifactId>project</artifactId>\r\n" +
                 "    <version>1</version>\r\n" +
                 "    <dependencies>\r\n" +
                 "        <dependency>\r\n" +
                 "            <groupId>group</groupId>\r\n" +
                 "            <artifactId>artifact</artifactId>\r\n" +
                 "            <version>version</version>\r\n" +
                 "        </dependency>\r\n" +
                 "    </dependencies>\r\n" +
                 "</project>", VfsUtil.loadText(myProjectPom));
  }

  private MavenModel getDomModel() {
    return MavenUtil.getMavenModel(myProject, myProjectPom);
  }

  private void formatAndSaveProjectPomDocument() {
    try {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myProjectPom);
      CodeStyleManager.getInstance(myProject).reformat(psiFile);
      Document d = FileDocumentManager.getInstance().getDocument(myProjectPom);
      FileDocumentManager.getInstance().saveDocument(d);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
