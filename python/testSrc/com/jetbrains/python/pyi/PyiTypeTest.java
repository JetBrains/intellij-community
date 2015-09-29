/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.fixtures.PyLightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public class PyiTypeTest extends PyTestCase {
  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return new PyLightProjectDescriptor(PYTHON_3_MOCK_SDK) {
      @Override
      public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
        createLibrary(model, "pyiStubs", "/community/python/testData/pyi/pyiStubs");
      }
    };
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.PYTHON35);
  }

  @Override
  public void tearDown() throws Exception {
    setLanguageLevel(null);
    super.tearDown();
  }

  private void doTest(@NotNull String expectedType) {
    myFixture.copyDirectoryToProject("pyi/type/" + getTestName(true), "");
    myFixture.copyDirectoryToProject("typing", "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    final String fileName = getTestName(false) + ".py";
    myFixture.configureByFile(fileName);
    final PsiElement element = myFixture.getElementAtCaret();
    assertNotNull("Could not find element at caret in: " + myFixture.getFile());
    assertInstanceOf(element, PyTypedElement.class);
    final PyTypedElement typedElement = (PyTypedElement)element;
    final Project project = element.getProject();
    final PsiFile containingFile = element.getContainingFile();
    assertType(expectedType, typedElement, TypeEvalContext.codeAnalysis(project, containingFile));
    assertType(expectedType, typedElement, TypeEvalContext.userInitiated(project, containingFile));
  }

  private static void assertType(@NotNull String expectedType, @NotNull PyTypedElement element, @NotNull TypeEvalContext context) {
    final PyType actual = context.getType(element);
    final String actualType = PythonDocumentationProvider.getTypeName(actual, context);
    assertEquals("Failed in " + context + " context", expectedType, actualType);
  }

  public void testFunctionParameter() {
    doTest("int");
  }

  public void testFunctionReturnType() {
    doTest("Optional[int]");
  }

  public void testFunctionType() {
    doTest("(x: int) -> dict");
  }

  public void testModuleAttribute() {
    doTest("int");
  }

  public void testPyiOnPythonPath() {
    doTest("int");
  }

  public void testOverloadedReturnType() {
    doTest("str");
  }
}
