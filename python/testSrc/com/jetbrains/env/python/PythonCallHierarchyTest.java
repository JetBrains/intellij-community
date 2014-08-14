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
package com.jetbrains.env.python;


import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.python.hierarchy.call.PyCalleeFunctionTreeStructure;
import com.jetbrains.python.hierarchy.call.PyCallerFunctionTreeStructure;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nullable;

public class PythonCallHierarchyTest extends PyEnvTestCase {

  @Override
  protected boolean runInDispatchThread() {
    return true;
  }

  public void testDynamic() {
    runPythonTest(new PyDebuggerTask("/hierarchy/call/Dynamic", "main.py") {

      private final Function<PsiElement, HierarchyTreeStructure> functionToCallerTreeStructure = new Function<PsiElement, HierarchyTreeStructure>() {
        @Nullable
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
        @Nullable
        @Override
        public HierarchyTreeStructure fun(PsiElement element) {
          if (!(element instanceof PyFunction)) {
            return null;
          }
          PyFunction function = (PyFunction)element;
          return new PyCalleeFunctionTreeStructure(myFixture.getProject(), function, HierarchyBrowserBaseEx.SCOPE_PROJECT);
        }
      };

      private String getBasePath() {
        return "hierarchy/call/Dynamic";
      }

      private void doTestCallHierarchy(String ... fileNames) throws Exception {
        String[] filePaths = new String[fileNames.length];
        int i = 0;
        for (String fileName: fileNames) {
          filePaths[i] = getBasePath() + "/" + fileName;
          i++;
        }
        String verificationCallerFilePath = getTestDataPath() + "/" + getBasePath() + "/" + "Dynamic" + "_caller_verification.xml";
        String verificationCalleeFilePath = getTestDataPath() + "/" + getBasePath() + "/" + "Dynamic" + "_callee_verification.xml";

        //myFixture.configureByFiles(filePaths);
        //myFixture.testCallHierarchy(functionToCallerTreeStructure, verificationCallerFilePath, filePaths);
        //myFixture.testCallHierarchy(functionToCalleeTreeStructure, verificationCalleeFilePath, filePaths);
      }

      @Override
      public void before() throws Exception {
        toggleBreakpoint(getScriptPath(), 29);
      }

      @Override
      public void testing() throws Exception {
        waitForPause();
        resume();
      }

      @Override
      public void after() {
        try {
          doTestCallHierarchy("main.py", "file_1.py");
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }
}
