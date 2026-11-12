/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.jetbrains.python.pyi;

import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Components;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
public class LegacyPyiTypeTest extends PyTestCase {

  private Disposable myDisposable;

  // return Disposable which undoes configuration
  public static Disposable addPyiStubsToContentRoot(CodeInsightTestFixture fixture) {
    final String path = fixture.getTestDataPath() + "/pyi/pyiStubs";
    final VirtualFile file = StandardFileSystems.local().refreshAndFindFileByPath(path);
    assertNotNull(file);
    file.refresh(false, true);
    ModuleRootModificationUtil.addContentRoot(fixture.getModule(), path);
    return () -> ModuleRootModificationUtil.updateModel(fixture.getModule(), model -> {
      for (ContentEntry entry : model.getContentEntries()) {
        if (file.equals(entry.getFile())) {
          model.removeContentEntry(entry);
        }
      }
    });
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myDisposable != null) {
        Disposer.dispose(myDisposable);
        myDisposable = null;
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void doTest(@NotNull String expectedType) {
    myFixture.copyDirectoryToProject("pyi/type/" + getTestName(true), "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    final String fileName = getTestName(false) + ".py";
    final var file = myFixture.configureByFile(fileName);
    final PsiElement element = myFixture.getElementAtCaret();
    assertInstanceOf(element, PyTypedElement.class);
    final PyTypedElement typedElement = (PyTypedElement)element;
    final Project project = element.getProject();
    assertType(expectedType, typedElement, TypeEvalContext.codeAnalysis(project, file));
    assertProjectFilesNotParsed(file);
    assertType(expectedType, typedElement, TypeEvalContext.userInitiated(project, file));
  }

  public void testPyiOnPythonPath() {
    myDisposable = addPyiStubsToContentRoot(myFixture);
    doTest("int");
  }
}
