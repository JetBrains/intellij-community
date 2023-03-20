// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.junit.Test;

public class MavenModelReadingAndWritingTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """);
  }

  @Test
  public void testReading() {
    MavenDomProjectModel model = getDomModel();

    assertEquals("test", model.getGroupId().getStringValue());
    assertEquals("project", model.getArtifactId().getStringValue());
    assertEquals("1", model.getVersion().getStringValue());
  }

  @Test
  public void testWriting() throws Exception {
    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      MavenDomProjectModel model = getDomModel();

      model.getGroupId().setStringValue("foo");
      model.getArtifactId().setStringValue("bar");
      model.getVersion().setStringValue("baz");

      formatAndSaveProjectPomDocument();
    }), null, null);

    assertSameLines("""
                      <?xml version="1.0"?>\r
                      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\r
                               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">\r
                          <modelVersion>4.0.0</modelVersion>\r
                          <groupId>foo</groupId>\r
                          <artifactId>bar</artifactId>\r
                          <version>baz</version>\r
                      </project>""",
                    VfsUtil.loadText(myProjectPom));
  }

  @Test
  public void testAddingADependency() throws Exception {
    CommandProcessor.getInstance().executeCommand(myProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      MavenDomProjectModel model = getDomModel();

      MavenDomDependency d = model.getDependencies().addDependency();
      d.getGroupId().setStringValue("group");
      d.getArtifactId().setStringValue("artifact");
      d.getVersion().setStringValue("version");

      formatAndSaveProjectPomDocument();
    }), null, null);

    assertSameLines("""
                      <?xml version="1.0"?>\r
                      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\r
                               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">\r
                          <modelVersion>4.0.0</modelVersion>\r
                          <groupId>test</groupId>\r
                          <artifactId>project</artifactId>\r
                          <version>1</version>\r
                          <dependencies>\r
                              <dependency>\r
                                  <groupId>group</groupId>\r
                                  <artifactId>artifact</artifactId>\r
                                  <version>version</version>\r
                              </dependency>\r
                          </dependencies>\r
                      </project>""", VfsUtil.loadText(myProjectPom));
  }

  private MavenDomProjectModel getDomModel() {
    return MavenDomUtil.getMavenDomProjectModel(myProject, myProjectPom);
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
