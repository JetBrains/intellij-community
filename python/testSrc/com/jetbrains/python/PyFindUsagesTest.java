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

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.*;

/**
 * @author yole
 */
public class PyFindUsagesTest extends PyTestCase {
  public void testInitUsages() {   // PY-292
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/InitUsages.py");
    assertEquals(1, usages.size());
  }

  public void testClassUsages() {   // PY-774
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ClassUsages.py");
    assertUsages(usages, "c = <caret>Cow()");
  }

  public void testReassignedLocalUsages() { // PY-527
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ReassignedLocalUsages.py");
    assertEquals(3, usages.size());
  }

  public void testImplicitlyResolvedUsages() {
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ImplicitlyResolvedUsages.py");
    assertEquals(1, usages.size());
  }

  public void testQualifiedVsUnqualifiedUsages() {  // PY-939
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/QualifiedVsUnqualifiedUsages.py");
    assertEquals(1, usages.size());
  }

  public void testGlobalUsages() { // PY-1167
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/GlobalUsages.py");
    assertEquals(4, usages.size());
  }

  public void testGlobalUsages2() { // PY-1167
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/GlobalUsages2.py");
    assertEquals(3, usages.size());
  }

  public void testNonGlobalUsages() { // PY-1179
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/NonGlobalUsages.py");
    assertUsages(usages, "<caret>a = 0");
  }

  public void testLambdaParameter() {
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/LambdaParameter.py");
    assertUsages(usages, "<caret>parm+1");
  }

  public void testUnresolvedClassInit() {   // PY-1450
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/UnresolvedClassInit.py");
    assertUsages(usages);
  }

  public void testImports() {  // PY-1514
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/Imports.py");
    assertUsages(usages, "import <caret>re", "<caret>re.compile");
  }

  public void testNestedFunctions() {  // PY-3118
    final Collection<UsageInfo> usages = doTest();
    assertUsages(usages, "<caret>bar = {}", "<caret>bar = []", "enumerate(<caret>bar)");
  }

  private Collection<UsageInfo> doTest() {
    return myFixture.testFindUsages("findUsages/" + getTestName(false) + ".py");
  }

  private void assertUsages(Collection<UsageInfo> usages, String... usageTexts) {
    assertEquals(usageTexts.length, usages.size());
    List<UsageInfo> sortedUsages = new ArrayList<UsageInfo>(usages);
    Collections.sort(sortedUsages, new Comparator<UsageInfo>() {
      @Override
      public int compare(UsageInfo o1, UsageInfo o2) {
        return o1.getElement().getTextRange().getStartOffset() - o2.getElement().getTextRange().getStartOffset();
      }
    });
    for (int i = 0; i < usageTexts.length; i++) {
      assertSameUsage(usageTexts[i], sortedUsages.get(i));
    }
  }

  private void assertSameUsage(String usageText, UsageInfo usageInfo) {
    int pos = usageText.indexOf("<caret>");
    assert pos >= 0;
    usageText = usageText.replace("<caret>", "");
    final int startIndex = usageInfo.getElement().getTextOffset() + usageInfo.getRangeInElement().getStartOffset() - pos;
    assertEquals(usageText, myFixture.getFile().getText().substring(startIndex, startIndex + usageText.length()));
  }

  public void testReassignedInstanceAttribute() {  // PY-4338
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ReassignedInstanceAttribute.py");
    assertEquals(5, usages.size());
  }

  public void testReassignedClassAttribute() {  // PY-4338
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ReassignedClassAttribute.py");
    assertEquals(6, usages.size());
  }
  
  public void testWrappedMethod() { // PY-5458
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/WrappedMethod.py");
    assertUsages(usages, "MyClass.<caret>testMethod",
                 "<caret>testMethod = staticmethod(testMethod)",
                 "testMethod = staticmethod(<caret>testMethod)");
  }

  // PY-7348
  public void testNamespacePackageUsages() {
    setLanguageLevel(LanguageLevel.PYTHON33);
    try {
      final Collection<UsageInfo> usages = findMultiFileUsages("a.py");
      assertEquals(3, usages.size());
    } finally {
      setLanguageLevel(null);
    }
  }

  public void testNameShadowing() {  // PY-6241
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/NameShadowing.py");
    assertEquals(2, usages.size());
  }

  public void testConditionalFunctions() {  // PY-1448
    final Collection<UsageInfo> usages = myFixture.testFindUsages("findUsages/ConditionalFunctions.py");
    assertEquals(3, usages.size());
  }

  private Collection<UsageInfo> findMultiFileUsages(String filename) {
    final String testName = getTestName(false);
    myFixture.copyDirectoryToProject("findUsages/" + testName, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
    myFixture.configureFromTempProjectFile(filename);
    final int flags = TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED;
    final PsiElement element = TargetElementUtilBase.findTargetElement(myFixture.getEditor(), flags);
    assertNotNull(element);
    return myFixture.findUsages(element);
  }
}
