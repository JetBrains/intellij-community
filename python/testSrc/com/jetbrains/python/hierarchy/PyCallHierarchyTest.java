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
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.hierarchy.call.PyCalleeFunctionTreeStructure;
import com.jetbrains.python.hierarchy.call.PyCallerFunctionTreeStructure;
import com.jetbrains.python.psi.PyFunction;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class PyCallHierarchyTest extends PyTestCase {
  private static final String CALLER_VERIFICATION_SUFFIX = "_caller_verification.xml";
  private static final String CALLEE_VERIFICATION_SUFFIX = "_callee_verification.xml";

  private String getBasePath() {
    return "hierarchy/call/" + getTestName(false);
  }

  private void configureByFiles(String ... fileNames) {
    String[] filePaths = new String[fileNames.length];
    int i = 0;
    for (String fileName: fileNames) {
      filePaths[i] = getBasePath() + "/" + fileName;
      i++;
    }
    myFixture.configureByFiles(filePaths);
  }

  private String getVerificationFilePath(final String suffix) {
    return getTestDataPath() + "/" + getBasePath() + "/" + getTestName(false) + suffix;
  }

  private String getVerificationCallerFilePath() {
    return getVerificationFilePath(CALLER_VERIFICATION_SUFFIX);
  }

  private String getVerificationCalleeFilePath() {
    return getVerificationFilePath(CALLEE_VERIFICATION_SUFFIX);
  }

  private void checkFunctionHierarchyTreeStructure(PyFunction function) throws Exception {
    HierarchyTreeStructureViewer.checkHierarchyTreeStructure(new PyCallerFunctionTreeStructure(myFixture.getProject(), function, HierarchyBrowserBaseEx.SCOPE_PROJECT),
                                                             JDOMUtil.loadDocument(new File(getVerificationCallerFilePath())));
    HierarchyTreeStructureViewer.checkHierarchyTreeStructure(new PyCalleeFunctionTreeStructure(myFixture.getProject(), function, HierarchyBrowserBaseEx.SCOPE_PROJECT),
                                                             JDOMUtil.loadDocument(new File(getVerificationCalleeFilePath())));
  }

  private void doTestCallHierarchy(String ... fileNames) throws Exception {
    configureByFiles(fileNames);

    final PsiElement targetElement = TargetElementUtilBase
      .findTargetElement(myFixture.getEditor(),
                         TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assert targetElement != null : "Cannot find referenced element";
    assert targetElement instanceof PyFunction : "Referenced element is not PyFunction";

    PyFunction function = (PyFunction) targetElement;
    checkFunctionHierarchyTreeStructure(function);
  }

  public void testSimple() throws Exception {
    doTestCallHierarchy("main.py");
  }

  public void testArgumentList() throws Exception {
    doTestCallHierarchy("main.py", "file_1.py");
  }

  public void testInheritance() throws Exception {
    doTestCallHierarchy("main.py");
  }

  public void testOverriddenMethod() throws Exception {
    doTestCallHierarchy("main.py", "file_1.py");
  }
}