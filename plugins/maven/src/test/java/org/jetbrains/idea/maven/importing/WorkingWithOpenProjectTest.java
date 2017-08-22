/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.idea.maven.MavenImportingTestCase;

import java.io.File;

public class WorkingWithOpenProjectTest extends MavenImportingTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");
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
