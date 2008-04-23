package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.dom.beans.MavenModel;
import org.jetbrains.idea.maven.dom.beans.Dependency;

public abstract class ModelReadingAndWritingTest extends MavenTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    createProjectPom("<groupId>test</groupId>" +
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

    assertEquals("<?xml version=\"1.0\"?>\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "    <groupId>foo</groupId>\n" +
                 "    <artifactId>bar</artifactId>\n" +
                 "    <version>baz</version>\n" +
                 "</project>",
                 getProjectPomDocument().getText());
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

    assertEquals("<?xml version=\"1.0\"?>\n" +
                 "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "    <groupId>test</groupId>\n" +
                 "    <artifactId>project</artifactId>\n" +
                 "    <version>1</version>\n" +
                 "    <dependencies>\n" +
                 "        <dependency>\n" +
                 "            <groupId>group</groupId>\n" +
                 "            <artifactId>artifact</artifactId>\n" +
                 "            <version>version</version>\n" +
                 "        </dependency>\n" +
                 "    </dependencies>\n" +
                 "</project>", getProjectPomDocument().getText());
  }

  private MavenModel getDomModel() {
    PsiFile f = getProjectPomPsiFile();
    DomFileElement<MavenModel> root = DomManager.getDomManager(myProject).getFileElement((XmlFile)f, MavenModel.class);
    return root.getRootElement();
  }

  private PsiFile getProjectPomPsiFile() {
    Document d = getProjectPomDocument();
    return PsiDocumentManager.getInstance(myProject).getPsiFile(d);
  }

  private Document getProjectPomDocument() {
    return FileDocumentManager.getInstance().getDocument(myProjectPom);
  }

  private void formatAndSaveProjectPomDocument() {
    try {
      CodeStyleManager.getInstance(myProject).reformat(getProjectPomPsiFile());
      FileDocumentManager.getInstance().saveDocument(getProjectPomDocument());
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
