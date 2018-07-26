// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.navigation;

import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class YAMLCopyReferenceTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/navigation/data/";
  }

  public void testSimpleConfig() {
    doTest("top.next.targetKey");
  }

  public void testArrayInPath() {
    doTest("top.next.list[1].targetKey");
  }

  public void testExplicitKey() {
    doTest("top.next.several line targetKey");
  }

  private void doTest(String result) {
    myFixture.configureByFile(getTestName(true) + ".yml");
    final PsiElement element = myFixture.getElementAtCaret();
    assertInstanceOf(element, YAMLKeyValue.class);
    final String qualifiedName = CopyReferenceAction.elementToFqn(element);
    assertEquals(result, qualifiedName);
  }
}
