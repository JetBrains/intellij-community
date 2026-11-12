package de.plushnikov.intellij.plugin.intention;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokIntentionFixture;

/**
 * Tests for {@link AddAllLombokBuilderMethodsAction}
 */
@TestApplication
public class AddAllLombokBuilderMethodsActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokIntentionFixture> intention =
    lombokIntentionFixture(projectFixture, "/intention/addAllBuilderMethods", () -> new AddAllLombokBuilderMethodsAction(),
                           this::wasInvocationSuccessful);

  private boolean wasInvocationSuccessful() {
    LightJavaCodeInsightTestFixture myFixture = intention.get().getFixture();
    try {
      // Compare the current state of the file with the expected state in the "after" file
      myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java", true);
      return true;
    } catch (Exception e) {
      // If there's an exception while comparing the files, the test is considered failed
      return false;
    }
  }

  // Test with basic @Builder annotation on class
  @Test
  public void testBasicBuilder() {
    intention.get().doTest();
  }

  // Test with @SuperBuilder annotation
  @Test
  public void testSuperBuilderSimple() {
    intention.get().doTest();
  }

  // Test with notNull fields
  @Test
  public void testBuilderWithNotNull() {
    intention.get().doTest();
  }

  // Test with AccessorsPrefix
  @Test
  public void testBuilderWithAccessorsPrefix() {
    intention.get().doTest();
  }

  // Test with custom builder/build method names
  @Test
  public void testBuilderWithCustomNames() {
    intention.get().doTest();
  }

  // Test with @Builder on constructor
  @Test
  public void testBuilderOnConstructor() {
    intention.get().doTest();
  }

  // Test with @Builder on a static method
  @Test
  public void testBuilderOnStaticMethod() {
    intention.get().doTest();
  }

  // Test with a predefined builder class
  @Test
  public void testBuilderWithPredefinedBuilder() {
    intention.get().doTest();
  }
}