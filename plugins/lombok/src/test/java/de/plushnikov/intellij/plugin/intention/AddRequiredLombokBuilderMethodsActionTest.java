package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokIntentionFixture;

/**
 * Tests for {@link AddRequiredLombokBuilderMethodsAction}
 */
@TestApplication
public class AddRequiredLombokBuilderMethodsActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LombokIntentionFixture> intention =
    lombokIntentionFixture(projectFixture, "/intention/addRequiredBuilderMethods", () -> new AddRequiredLombokBuilderMethodsAction(),
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

  // Test with basic @Builder annotation on class and @NonNull fields
  @Test
  public void testBasicBuilderWithNonNull() {
    intention.get().doTest();
  }

  // Test with @SuperBuilder annotation and @NonNull fields
  @Test
  public void testSuperBuilderWithNonNull() {
    intention.get().doTest();
  }

  // Test with different NotNull annotations from various frameworks
  @Test
  public void testBuilderWithDifferentNotNullAnnotations() {
    LightJavaCodeInsightTestFixture myFixture = intention.get().getFixture();
    final NullableNotNullManager notNullManager = NullableNotNullManager.getInstance(myFixture.getProject());
    List<String> existingNotNulls = new ArrayList<>(notNullManager.getNotNulls());
    existingNotNulls.add("javax.validation.constraints.NotNull");
    notNullManager.setNotNulls(existingNotNulls.toArray(String[]::new));

    intention.get().doTest();
  }

  // Test with AccessorsPrefix and @NonNull fields
  @Test
  public void testBuilderWithAccessorsPrefixAndNonNull() {
    intention.get().doTest();
  }

  // Test with custom builder/build method names and @NonNull fields
  @Test
  public void testBuilderWithCustomNamesAndNonNull() {
    intention.get().doTest();
  }

  // Test with @Builder on constructor and @NonNull fields
  @Test
  public void testBuilderOnConstructorWithNonNull() {
    intention.get().doTest();
  }

  // Test with @Builder on static method and @NonNull fields
  @Test
  public void testBuilderOnStaticMethodWithNonNull() {
    intention.get().doTest();
  }

  // Test with predefined builder class and @NonNull fields
  @Test
  public void testBuilderWithPredefinedBuilderAndNonNull() {
    intention.get().doTest();
  }
}