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

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalInspection;
import com.jetbrains.python.psi.PythonVisitorFilter;
import org.jetbrains.annotations.NotNull;

public class PyiInspectionsTest extends PyTestCase {

  private Disposable myRootsDisposable;

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myRootsDisposable != null) {
        Disposer.dispose(myRootsDisposable);
        myRootsDisposable = null;
      }

      // clear cached extensions
      // see com.jetbrains.python.PyFunctionTypeAnnotationParsingTest.tearDown()
      PythonVisitorFilter.INSTANCE.removeExplicitExtension(PythonLanguage.INSTANCE, (visitorClass, file) -> false);
      PythonVisitorFilter.INSTANCE.removeExplicitExtension(PyiLanguageDialect.getInstance(), (visitorClass, file) -> false);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private void doTestByExtension(@NotNull Class<? extends LocalInspectionTool> inspectionClass, @NotNull String extension) {
    doTestByFileName(inspectionClass, getTestName(false) + extension);
  }

  private void doTestByFileName(@NotNull Class<? extends LocalInspectionTool> inspectionClass, String fileName) {
    myFixture.copyDirectoryToProject("pyi/inspections/" + getTestName(true), "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    final PsiFile file = myFixture.configureByFile(fileName);
    myFixture.enableInspections(inspectionClass);
    myFixture.checkHighlighting(true, false, true);
    assertProjectFilesNotParsed(file);
    assertSdkRootsNotParsed(file);
  }

  private void doPyTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass) {
    doTestByExtension(inspectionClass, ".py");
  }

  private void doPyiTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass) {
    doTestByExtension(inspectionClass, ".pyi");
  }

  public void testUnresolvedModuleAttributes() {
    doPyTest(PyUnresolvedReferencesInspection.class);
  }

  public void testHiddenPyiImports() {
    doPyTest(PyUnresolvedReferencesInspection.class);
  }

  public void testUnresolvedClassAttributes() {
    doPyTest(PyUnresolvedReferencesInspection.class);
  }

  public void testOverloads() {
    doPyTest(PyTypeCheckerInspection.class);
  }

  public void testOverloadsWithDifferentNumberOfParameters() {
    doPyTest(PyTypeCheckerInspection.class);
  }

  public void testOverloadedGenerics() {
    doPyTest(PyTypeCheckerInspection.class);
  }

  public void testPyiUnusedParameters() {
    doPyiTest(PyUnusedLocalInspection.class);
  }

  public void testPyiStatementEffect() {
    doPyiTest(PyStatementEffectInspection.class);
  }

  // PY-19375
  public void testPyiCompatibilityAndEllipsis() {
    doPyiTest(PyCompatibilityInspection.class);
  }

  // PY-19375
  public void testPyiMissingOrEmptyDocstring() {
    doPyiTest(PyMissingOrEmptyDocstringInspection.class);
  }

  // PY-19374
  public void testPyiClassForwardReferences() {
    doPyiTest(PyUnresolvedReferencesInspection.class);
  }

  // PY-49004
  public void testPyiTopLevelResolvedForwardReferencesInAnnotations() {
    doPyiTest(PyUnresolvedReferencesInspection.class);
  }

  public void testPyiTopLevelUnboundForwardReferencesInAnnotations() {
    doPyiTest(PyUnboundLocalVariableInspection.class);
  }

  public void testPyiUnusedImports() {
    doPyiTest(PyUnresolvedReferencesInspection.class);
  }

  public void testPyiRelativeImports() {
    myRootsDisposable = PyiTypeTest.addPyiStubsToContentRoot(myFixture);
    doTestByFileName(PyUnresolvedReferencesInspection.class, "package_with_stub_in_path/a.pyi");
  }

  // PY-16868
  public void testPropertyDefinition() {
    doPyiTest(PyPropertyDefinitionInspection.class);
  }

  // PY-33486
  public void testMissedSuperInitCall() {
    doPyiTest(PyMissingConstructorInspection.class);
  }
}
