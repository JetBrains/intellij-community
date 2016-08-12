/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author vlan
 */
public class Py3UnresolvedReferencesInspectionTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "inspections/PyUnresolvedReferencesInspection3K/";

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  private void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> {
      myFixture.configureByFile(TEST_DIRECTORY + getTestName(true) + ".py");
      myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
      myFixture.checkHighlighting(true, false, false);
    });
  }

  private void doMultiFileTest(@NotNull final String filename) {
    doMultiFileTest(filename, Collections.emptyList());
  }

  private void doMultiFileTest(@NotNull final String filename, @NotNull List<String> sourceRoots) {
    runWithLanguageLevel(LanguageLevel.PYTHON35, () -> {
      final String testName = getTestName(false);
      myFixture.copyDirectoryToProject(TEST_DIRECTORY + testName, "");
      final Module module = myFixture.getModule();
      for (String root : sourceRoots) {
        PsiTestUtil.addSourceRoot(module, myFixture.findFileInTempDir(root));
      }
      try {
        myFixture.configureFromTempProjectFile(filename);
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        myFixture.checkHighlighting(true, false, false);
      }
      finally {
        for (String root : sourceRoots) {
          PsiTestUtil.removeSourceRoot(module, myFixture.findFileInTempDir(root));
        }
      }
    });
  }

  public void testNamedTuple() {
    doTest();
  }

  public void testNamedTupleAssignment() {
    doMultiFileTest("a.py");
  }

  // TODO: Currently there are no stubs for namedtuple() in the base classes list and no indicators for forcing stub->AST
  public void _testNamedTupleBaseStub() {
    doMultiFileTest("a.py");
  }

  // PY-10208
  public void testMetaclassMethods() {
    doTest();
  }

  public void testMetaclassStub() {
    doMultiFileTest("a.py");
    final Project project = myFixture.getProject();
    Collection<PyClass> classes = PyClassNameIndex.find("M", project, GlobalSearchScope.allScope(project));
    for (PyClass cls : classes) {
      final PsiFile file = cls.getContainingFile();
      if (file instanceof PyFile) {
        assertNotParsed((PyFile)file);
      }
    }
  }

  // PY-9011
  public void testDatetimeDateAttributesOutsideClass() {
    doMultiFileTest("a.py");
  }

  public void testObjectNewAttributes() {
    doTest();
  }

  public void testEnumMemberAttributes() {
    doMultiFileTest("a.py");
  }

  // PY-12864
  public void testAttributesOfUnresolvedTypeFile() {
    doTest();
  }

  // PY-14385
  public void testNotImportedSubmodulesOfNamespacePackage() {
    doMultiFileTest("main.py");
  }

  // PY-15017
  public void testClassLevelReferenceInMethodAnnotation() {
    doTest();
  }

  // PY-17841
  public void testTypingParameterizedTypeIndexing() {
    myFixture.copyDirectoryToProject("typing", "");
    doTest();
  }

  // PY-17841
  public void testMostDerivedMetaClass() {
    doTest();
  }

  // PY-17841
  public void testNoMostDerivedMetaClass() {
    doTest();
  }

  // PY-19028
  public void testDecodeBytesAfterSlicing() {
    doTest();
  }

  // PY-13734
  public void testDunderClass() {
    doTest();
  }

  // PY-19085
  public void testReAndRegexFullmatch() {
    doTest();
  }

  // PY-19775
  public void testAsyncInitMethod() {
    doTest();
  }

  // PY-19691
  public void testNestedPackageNamedAsSourceRoot() {
    doMultiFileTest("a.py", Collections.singletonList("lib1"));
  }
}
