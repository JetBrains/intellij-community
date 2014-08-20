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
package com.jetbrains.env.python.hierarchy;

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.hierarchy.HierarchyTreeStructureViewer;
import com.jetbrains.python.hierarchy.call.PyCalleeFunctionTreeStructure;
import com.jetbrains.python.hierarchy.call.PyCallerFunctionTreeStructure;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jdom.Document;

import java.io.File;


public class PyCallHierarchyTask extends PyDebuggerTask {

  private static final String TARGET_FUNCTION_NAME = "target_func";
  private static final String CALLER_VERIFICATION_SUFFIX = "_caller_verification.xml";
  private static final String CALLEE_VERIFICATION_SUFFIX = "_callee_verification.xml";

  private final String myTestName;

  public PyCallHierarchyTask(String testName, String workingFolder, String scriptName) {
    super(workingFolder, scriptName);
    myTestName = testName;
  }

  private String getVerificationFilePath(final String suffix) {
    return getWorkingFolder() + "/" + myTestName + suffix;
  }

  private String getVerificationCallerFilePath() {
    return getVerificationFilePath(CALLER_VERIFICATION_SUFFIX);
  }

  private String getVerificationCalleeFilePath() {
    return getVerificationFilePath(CALLEE_VERIFICATION_SUFFIX);
  }

  @Override
  public void setUp(String testName) throws Exception {
    super.setUp(testName);
    PyDebuggerOptionsProvider debuggerOptions = PyDebuggerOptionsProvider.getInstance(getProject());
    debuggerOptions.setSaveCallSignatures(true);
  }

  @Override
  public void tearDown() throws Exception {
    PyDebuggerOptionsProvider debuggerOptions = PyDebuggerOptionsProvider.getInstance(getProject());
    debuggerOptions.setSaveCallSignatures(false);
    super.tearDown();
  }

  @Override
  public void runTestOn(String sdkHome) throws Exception {
    super.runTestOn(sdkHome);
    final Document callerDocument = JDOMUtil.loadDocument(new File(getVerificationCallerFilePath()));
    final Document calleeDocument = JDOMUtil.loadDocument(new File(getVerificationCalleeFilePath()));
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        checkHierarchyTreeStructure(callerDocument, calleeDocument);
      }
    });
  }

  private void checkHierarchyTreeStructure(Document callerDocument, Document calleeDocument) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(getScriptPath());
    assert vFile != null;
    final PsiFile file = PsiManager.getInstance(myFixture.getProject()).findFile(vFile);
    assert file instanceof PyFile;
    PyFunction function = ((PyFile)file).findTopLevelFunction(TARGET_FUNCTION_NAME);
    assert function != null : "Cannot find target function";
    HierarchyTreeStructureViewer
      .checkHierarchyTreeStructure(new PyCallerFunctionTreeStructure(myFixture.getProject(), function, HierarchyBrowserBaseEx.SCOPE_PROJECT),
                                   callerDocument);
    HierarchyTreeStructureViewer.checkHierarchyTreeStructure(new PyCalleeFunctionTreeStructure(myFixture.getProject(), function, HierarchyBrowserBaseEx.SCOPE_PROJECT),
                                                             calleeDocument);
  }
}
