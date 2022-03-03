// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import org.junit.Test;

import java.io.File;

public class WorkingWithOpenProjectTest extends MavenMultiVersionImportingTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
  }

  @Test
  public void testShouldNotFailOnNewEmptyPomCreation() {
    createModulePom("module", ""); // should not throw an exception
  }

  @Test
  public void testShouldNotFailOnAddingNewContentRootWithAPomFile() throws Exception {
    File newRootDir = new File(myDir, "newRoot");
    newRootDir.mkdirs();

    File pomFile = new File(newRootDir, "pom.xml");
    pomFile.createNewFile();

    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newRootDir);

    PsiTestUtil.addContentRoot(getModule("project"), root);  // should not throw an exception
  }
  
  public void _testSavingAllDocumentBeforeReimport() {
    // cannot make it work die to order of document listeners

    myProjectsManager.listenForExternalChanges();
    final Document d = FileDocumentManager.getInstance().getDocument(myProjectPom);
    WriteCommandAction.runWriteCommandAction(null, () -> d.setText(createPomXml("<groupId>test</groupId>" +
                                                                              "<artifactId>project</artifactId>" +
                                                                              "<version>1</version>" +

                                                                              "<dependencies>" +
                                                                              "  <dependency>" +
                                                                              "    <groupId>junit</groupId>" +
                                                                              "    <artifactId>junit</artifactId>" +
                                                                              "    <version>4.0</version>" +
                                                                              "  </dependency>" +
                                                                              "</dependencies>")));

    resolveDependenciesAndImport();

    assertModuleLibDep("project", "Maven: junit:junit:4.0");
  }
}
