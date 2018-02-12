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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.hierarchy.call.PyCalleeFunctionTreeStructure;
import com.jetbrains.python.hierarchy.call.PyCallerFunctionTreeStructure;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nullable;

/**
 * @author novokrest
 */
public class PyCallHierarchyTest extends PyTestCase {
  private static final String CALLER_VERIFICATION_SUFFIX = "_caller_verification.xml";
  private static final String CALLEE_VERIFICATION_SUFFIX = "_callee_verification.xml";

  public static String dump(final HierarchyTreeStructure treeStructure, @Nullable HierarchyNodeDescriptor descriptor) {
    StringBuilder s = new StringBuilder();
    dump(treeStructure, descriptor, 0, s);
    return s.toString();
  }

  private static void dump(final HierarchyTreeStructure treeStructure,
                           @Nullable HierarchyNodeDescriptor descriptor,
                           int level,
                           StringBuilder b) {
    if (level > 10) {
      for(int i = 0; i<level; i++) b.append("  ");
      b.append("<Probably infinite part skipped>\n");
      return;
    }
    if(descriptor==null) descriptor = (HierarchyNodeDescriptor)treeStructure.getRootElement();
    for(int i = 0; i<level; i++) b.append("  ");
    descriptor.update();
    b.append("<node text=\"").append(descriptor.getHighlightedText().getText()).append("\"")
      .append(treeStructure.getBaseDescriptor() == descriptor ? " base=\"true\"" : "");

    final Object[] children = treeStructure.getChildElements(descriptor);
    if(children.length>0) {
      b.append(">\n");
      for (Object o : children) {
        HierarchyNodeDescriptor d = (HierarchyNodeDescriptor)o;
        dump(treeStructure, d, level + 1, b);
      }
      for(int i = 0; i<level; i++) b.append("  ");
      b.append("</node>\n");
    } else {
      b.append("/>\n");
    }
  }

  private String getBasePath() {
    return "hierarchy/call/Static/" + getTestName(false);
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

  private void checkHierarchyTreeStructure(PyFunction function) {
    final PyCallerFunctionTreeStructure callerStructure = new PyCallerFunctionTreeStructure(myFixture.getProject(), function,
                                                                                            HierarchyBrowserBaseEx.SCOPE_PROJECT);
    assertSameLinesWithFile(getVerificationCallerFilePath(), dump(callerStructure, null));
    final PyCalleeFunctionTreeStructure calleeStructure = new PyCalleeFunctionTreeStructure(myFixture.getProject(), function,
                                                                                            HierarchyBrowserBaseEx.SCOPE_PROJECT);
    assertSameLinesWithFile(getVerificationCalleeFilePath(), dump(calleeStructure, null));
  }

  private void doTestCallHierarchy(String ... fileNames) {
    configureByFiles(fileNames);

    final PsiElement targetElement = TargetElementUtil
      .findTargetElement(myFixture.getEditor(),
                         TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assert targetElement != null : "Cannot find referenced element";
    assert targetElement instanceof PyFunction : "Referenced element is not PyFunction";

    PyFunction function = (PyFunction) targetElement;
    checkHierarchyTreeStructure(function);
  }

  public void testSimple() {
    doTestCallHierarchy("main.py");
  }

  public void testArgumentList() {
    doTestCallHierarchy("main.py", "file_1.py");
  }

  public void testDefaultValue() {
    doTestCallHierarchy("main.py");
  }

  public void testLambda() {
    doTestCallHierarchy("main.py", "file_1.py");
  }

  public void testNestedCall() {
    doTestCallHierarchy("main.py", "file_1.py");
  }

  public void testInheritance() {
    doTestCallHierarchy("main.py");
  }

  public void testOverriddenMethod() {
    doTestCallHierarchy("main.py", "file_1.py");
  }

  public void testInnerFunction() {
    doTestCallHierarchy("main.py");
  }

  public void testConstructor() {
    doTestCallHierarchy("main.py");
  }

  public void testParentheses() {
    doTestCallHierarchy("main.py", "file_1.py");
  }
}