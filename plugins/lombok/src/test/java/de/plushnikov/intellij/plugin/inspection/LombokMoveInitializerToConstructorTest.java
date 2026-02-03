package de.plushnikov.intellij.plugin.inspection;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;


public class LombokMoveInitializerToConstructorTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  public void testMoveInitializerToConstructor() {
    myFixture.configureByText("Test.java", """
      import lombok.AllArgsConstructor;

      @AllArgsConstructor
      public class Main {
          int x;
          int y = <caret>3;
      }
      """);
    assertEmpty(myFixture.filterAvailableIntentions("Move initializer to constructor"));
  }

}
