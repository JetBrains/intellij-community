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
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.codeFragment.CannotCreateCodeFragmentException;
import com.intellij.codeInsight.codeFragment.CodeFragment;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.codeInsight.codeFragment.PyCodeFragmentUtil;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyFile;

import java.util.TreeSet;

/**
 * @author oleg
 */
public class PyCodeFragmentTest extends LightMarkedTestCase {
  @Override
  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/codeInsight/codefragment/";
  }

  final private String BEGIN_MARKER = "<begin>";
  final private String END_MARKER = "<end>";
  final private String RESULT_MARKER = "<result>";

  private void doTest(Pair<String, String>... files2Create) throws Exception {
    final String testName = getTestName(false).toLowerCase();
    final String fullPath = getTestDataPath() + testName + ".test";

    final VirtualFile vFile = getVirtualFileByName(fullPath);
    String fileText = StringUtil.convertLineSeparators(VfsUtil.loadText(vFile), "\n");

    final int beginMarker = fileText.indexOf(BEGIN_MARKER);
    final int endMarker = fileText.indexOf(END_MARKER);
    final int resultMarker = fileText.indexOf(RESULT_MARKER);
    assertTrue(beginMarker != -1);
    assertTrue(endMarker != -1);
    assertTrue(resultMarker != -1);

    final StringBuilder builder = new StringBuilder();
    builder.append(fileText.substring(0, beginMarker));
    builder.append(fileText.substring(beginMarker + BEGIN_MARKER.length(), endMarker));
    builder.append((fileText.substring(endMarker + END_MARKER.length(), resultMarker)));

    final String result = fileText.substring(resultMarker + RESULT_MARKER.length());

    // Create additional files
    for (Pair<String, String> pair : files2Create) {
      myFixture.addFileToProject(pair.first, pair.second);
    }

    final PyFile file = (PyFile)myFixture.addFileToProject(testName + ".py", builder.toString());
    check(file, beginMarker, endMarker, result);
  }

  private void check(final PyFile myFile, final int beginMarker, final int endMarker, final String result) {
    final PsiElement startElement = myFile.findElementAt(beginMarker);
    final PsiElement endElement = myFile.findElementAt(endMarker - BEGIN_MARKER.length());
    PsiElement context = PsiTreeUtil.findCommonParent(startElement, endElement);
    if (!(context instanceof ScopeOwner)) {
      context = PsiTreeUtil.getParentOfType(context, ScopeOwner.class);
    }
    final StringBuffer buffer = new StringBuffer();
    try {
      final CodeFragment fragment = PyCodeFragmentUtil.createCodeFragment((ScopeOwner)context, startElement, endElement);
      if (fragment.isReturnInstructionInside()) {
        buffer.append("Return instruction inside found").append("\n");
      }
      buffer.append("In:\n");
      for (String inputVariable : new TreeSet<>(fragment.getInputVariables())) {
        buffer.append(inputVariable).append('\n');
      }
      buffer.append("Out:\n");
      for (String outputVariable : new TreeSet<>(fragment.getOutputVariables())) {
        buffer.append(outputVariable).append('\n');
      }
    }
    catch (CannotCreateCodeFragmentException e) {
      assertEquals(result.trim(), e.getMessage());
      return;
    }
    assertEquals(result.trim(), buffer.toString().trim());
  }

  public void testImportBefore() throws Exception {
    doTest(Pair.create("foo.py", ""));
  }

  public void testImportBeforeUseInside() throws Exception {
    doTest(Pair.create("foo.py", ""));
  }

  public void testImportInsideUseAfter() throws Exception {
    doTest(Pair.create("foo.py", ""));
  }

  public void testImportAfter() throws Exception {
    doTest(Pair.create("foo.py", ""));
  }


  public void testSimple() throws Exception {
    doTest();
  }

  public void testWhile() throws Exception {
    doTest();
  }

  public void testEmpty() throws Exception {
    doTest();
  }

  public void testOut() throws Exception {
    doTest();
  }


  public void testExpression() throws Exception {
    doTest();
  }

  public void testParameters() throws Exception {
    doTest();
  }

  public void testVariables() throws Exception {
    doTest();
  }

  public void testVariablesEmptyOut() throws Exception {
    doTest();
  }

  public void testVariablesEmptyIn() throws Exception {
    doTest();
  }

  public void testExpression2() throws Exception {
    doTest();
  }

  public void testAugAssignment() throws Exception {
    doTest();
  }

  public void testClass() throws Exception {
    doTest();
  }

  public void testForIfReturn() throws Exception {
    doTest();
  }

  public void testRaise2102() throws Exception {
    doTest();
  }

}

