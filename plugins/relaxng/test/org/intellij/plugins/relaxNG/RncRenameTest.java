/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 25.07.2007
 */
public class RncRenameTest extends HighlightingTestBase {

  protected CodeInsightTestFixture createFixture(IdeaTestFixtureFactory factory) {
    return createContentFixture(factory);
  }

  public String getTestDataPath() {
    return "rename/rnc";
  }

  public void testRenameDefintion1() throws Throwable {
    doTestRename("rename-definition-1", "bar");
  }

  public void testRenameDefintion2() throws Throwable {
    doTestRename("rename-definition-2", "element");
  }

  public void testRenameDefintion3() throws Throwable {
    doTestRename("rename-definition-3", "bar");
  }

  public void testRenameNsPrefix1() throws Throwable {
    doTestRename("rename-ns-prefix-1", "bar");
  }

  public void testRenameDatatypePrefix1() throws Throwable {
    doTestRename("rename-datatype-prefix-1", "bar");
  }

  public void testRenameIncludedFile() throws Throwable {
    myTestFixture.copyFileToProject("rename-in-include-ref.rnc");
    
    final Project project = myTestFixture.getProject();
    final RefactoringFactory factory = RefactoringFactory.getInstance(project);

    String fullPath = myTestFixture.getTempDirPath() + "/" + "rename-in-include-ref.rnc";

    final VirtualFile copy = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'));
    assert copy != null : "file " + fullPath + " not found";

    final PsiFile file = PsiManager.getInstance(project).findFile(copy);
    assertNotNull(file);

    new WriteCommandAction.Simple(project) {
      protected void run() throws Throwable {
        myTestFixture.configureByFile("rename-in-include.rnc");
        final RenameRefactoring refactoring = factory.createRename(file, "rename-after.rnc");
        refactoring.setPreviewUsages(false);
        refactoring.setSearchInComments(false);
        refactoring.setSearchInNonJavaFiles(true);
        refactoring.run();
        myTestFixture.checkResultByFile("rename-in-include_after.rnc");
      }
    }.execute().throwException();

    assertEquals("rename-after.rnc", file.getName());
  }

  private void doTestRename(String name, String newName) throws Throwable {
    doTestRename(name, "rnc", newName);
  }
}