package com.intellij.lang.properties;

import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public final class PropertyReferenceTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_25;
  }

  public void testNoReferenceToJdk() {
    myFixture.configureByText("Test.java", """
                                public class Test {
                                  void test() {
                                    String s = "i<caret>d";
                                  }
                                }
                                """);
    PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
    assertNotNull(ref);
    assertNull(ref.resolve());
  }
}
