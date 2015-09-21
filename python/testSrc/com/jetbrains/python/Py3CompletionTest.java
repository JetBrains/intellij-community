/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.List;

/**
 * @author yole
 */
public class Py3CompletionTest extends PyTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  public void testPropertyDecorator() {
    doTest();
  }

  public void testPropertyAfterAccessor() {  // PY-5951
    doTest();
  }

  public void testNamedTuple() {
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    final List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertTrue(strings.contains("lat"));
    assertTrue(strings.contains("long"));
  }

  public void testNamedTupleBaseClass() {
    doTest();
  }

  // PY-13157
  public void testMetaClass() {
    doTestByText("class C(meta<caret>):\n" +
                 "    pass\n");
    myFixture.checkResult("class C(metaclass=):\n" +
                          "    pass\n");
  }

  private void doTest() {
    CamelHumpMatcher.forceStartMatching(getTestRootDisposable());
    final String testName = getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  private void doMultiFileTest() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("a.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(true) + "/a.after.py");
  }

  private List<String> doTestByText(String text) {
    myFixture.configureByText(PythonFileType.INSTANCE, text);
    myFixture.completeBasic();
    return myFixture.getLookupElementStrings();
  }

  // PY-4073
  public void testSpecialFunctionAttributesPy3() throws Exception {
    setLanguageLevel(LanguageLevel.PYTHON32);
    try {
      List<String> suggested = doTestByText("def func(): pass; func.func_<caret>");
      assertNotNull(suggested);
      assertEmpty(suggested);

      suggested = doTestByText("def func(): pass; func.__<caret>");
      assertNotNull(suggested);
      assertContainsElements(suggested, "__defaults__", "__globals__", "__closure__",
                             "__code__", "__name__", "__doc__", "__dict__", "__module__");
      assertContainsElements(suggested, "__annotations__", "__kwdefaults__");
    }
    finally {
      setLanguageLevel(null);
    }
  }

  // PY-7375
  public void testImportNamespacePackage() {
    doMultiFileTest();
  }

  // PY-5422
  public void testImportQualifiedNamespacePackage() {
    doMultiFileTest();
  }

  // PY-6477
  public void testFromQualifiedNamespacePackageImport() {
    doMultiFileTest();
  }

  public void testImportNestedQualifiedNamespacePackage() {
    doMultiFileTest();
  }

  // PY-7376
  public void testRelativeFromImportInNamespacePackage() {
    doMultiFileTestInsideNamespacePackage();
  }

  // PY-7376
  public void testRelativeFromImportInNamespacePackage2() {
    doMultiFileTestInsideNamespacePackage();
  }

  private void doMultiFileTestInsideNamespacePackage() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.configureByFile("nspkg1/a.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(getTestName(true) + "/nspkg1/a.after.py");
  }

  // PY-14385
  public void testNotImportedSubmodulesOfNamespacePackage() {
    doMultiFileTest();
  }

  // PY-15390
  public void testMatMul() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  // PY-11214
  public void testDunderNext() {
    doTest();
  }

  public void testAsync() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  public void testAwait() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, new Runnable() {
      @Override
      public void run() {
        doTest();
      }
    });
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/completion";
  }
}
