/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.hierarchy;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.hierarchy.call.CallerMethodsTreeStructure;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestBase;
import com.intellij.util.Function;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.hierarchy.call.PyCalleeFunctionTreeStructure;
import com.jetbrains.python.hierarchy.call.PyCallerFunctionTreeStructure;
import com.jetbrains.python.psi.PyFunction;


public class PyCallHierarchyTest extends PyTestCase { // extends HierarchyViewTestBase {

  private final Function<PsiElement, HierarchyTreeStructure> functionToCallerTreeStructure = new Function<PsiElement, HierarchyTreeStructure>() {
    @Override
    public HierarchyTreeStructure fun(PsiElement element) {
      if (!(element instanceof PyFunction)) {
        return null;
      }
      PyFunction function = (PyFunction)element;
      return new PyCallerFunctionTreeStructure(myFixture.getProject(), function, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }
  };

  private final Function<PsiElement, HierarchyTreeStructure> functionToCalleeTreeStructure = new Function<PsiElement, HierarchyTreeStructure>() {
    @Override
    public HierarchyTreeStructure fun(PsiElement element) {
      if (!(element instanceof PyFunction)) {
        return null;
      }
      PyFunction function = (PyFunction)element;
      return new PyCalleeFunctionTreeStructure(myFixture.getProject(), function, HierarchyBrowserBaseEx.SCOPE_PROJECT);
    }
  };

  protected String getBasePath() {
    return "hierarchy/call/" + getTestName(false);
  }

  private void testCallHierarchy(String ... fileNames) throws Exception {
    String[] filePaths = new String[fileNames.length];
    int i = 0;
    for (String fileName: fileNames) {
      filePaths[i] = getBasePath() + "/" + fileName;
      i++;
    }
    String verificationCallerFilePath = getTestDataPath() + "/" + getBasePath() + "/" + getTestName(false) + "_caller_verification.xml";
    String verificationCalleeFilePath = getTestDataPath() + "/" + getBasePath() + "/" + getTestName(false) + "_callee_verification.xml";
    myFixture.configureByFiles(filePaths);
    myFixture.testCallHierarchy(functionToCallerTreeStructure, verificationCallerFilePath, filePaths);
    myFixture.testCallHierarchy(functionToCalleeTreeStructure, verificationCalleeFilePath, filePaths);
  }

  public void testSimple() throws Exception {
    testCallHierarchy("main.py");
  }

  public void testArgumentList() throws Exception {
    testCallHierarchy("main.py", "file_1.py");
  }

  public void testInheritance() throws Exception {
    testCallHierarchy("main.py");
  }
}
