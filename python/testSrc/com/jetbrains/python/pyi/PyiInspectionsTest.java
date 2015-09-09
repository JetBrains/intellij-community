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

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.psi.PsiDocumentManager;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyStatementEffectInspection;
import com.jetbrains.python.inspections.PyTypeCheckerInspection;
import com.jetbrains.python.inspections.PyUnusedLocalInspection;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyiInspectionsTest extends PyTestCase {
  private void doTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass, @NotNull String extension) {
    myFixture.copyDirectoryToProject("pyi/inspections/" + getTestName(true), "");
    myFixture.copyDirectoryToProject("typing", "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    final String fileName = getTestName(false) + extension;
    myFixture.configureByFile(fileName);
    myFixture.enableInspections(inspectionClass);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doPyTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass) {
    doTest(inspectionClass, ".py");
  }

  private void doPyiTest(@NotNull Class<? extends LocalInspectionTool> inspectionClass) {
    doTest(inspectionClass, ".pyi");
  }

  public void testUnresolvedModuleAttributes() {
    doPyTest(PyUnresolvedReferencesInspection.class);
  }

  public void testUnresolvedClassAttributes() {
    doPyTest(PyUnresolvedReferencesInspection.class);
  }

  public void testOverloads() {
    doPyTest(PyTypeCheckerInspection.class);
  }

  public void testPyiUnusedParameters() {
    doPyiTest(PyUnusedLocalInspection.class);
  }

  public void testPyiStatementEffect() {
    doPyiTest(PyStatementEffectInspection.class);
  }
}
