package com.jetbrains.python;

import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import java.util.*;

/**
 * @author yole
 */
public class PyFindUsagesTest extends PyLightFixtureTestCase {
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
      assertSameUsage(usageTexts [i], sortedUsages.get(i));
    }
  }

  private void assertSameUsage(String usageText, UsageInfo usageInfo) {
    int pos = usageText.indexOf("<caret>");
    assert pos >= 0;
    usageText = usageText.replace("<caret>", "");
    final int startIndex = usageInfo.getElement().getTextOffset() + usageInfo.getRangeInElement().getStartOffset() - pos;
    assertEquals(usageText, myFixture.getFile().getText().substring(startIndex, startIndex + usageText.length()));
  }
}
