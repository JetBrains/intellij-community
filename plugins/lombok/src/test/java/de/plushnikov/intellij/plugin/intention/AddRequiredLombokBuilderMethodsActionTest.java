package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.modcommand.ModCommandAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link AddRequiredLombokBuilderMethodsAction}
 */
public class AddRequiredLombokBuilderMethodsActionTest extends LombokIntentionActionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/addRequiredBuilderMethods";
  }

  @Override
  public ModCommandAction getAction() {
    return new AddRequiredLombokBuilderMethodsAction();
  }

  @Override
  public boolean wasInvocationSuccessful() {
    try {
      // Compare the current state of the file with the expected state in the "after" file
      myFixture.checkResultByFile("after" + getTestName(false) + ".java", true);
      return true;
    } catch (Exception e) {
      // If there's an exception while comparing the files, the test is considered failed
      return false;
    }
  }

  // Test with basic @Builder annotation on class and @NonNull fields
  public void testBasicBuilderWithNonNull() {
    doTest();
  }

  // Test with @SuperBuilder annotation and @NonNull fields
  public void testSuperBuilderWithNonNull() {
    doTest();
  }

  // Test with different NotNull annotations from various frameworks
  public void testBuilderWithDifferentNotNullAnnotations() {
    final NullableNotNullManager notNullManager = NullableNotNullManager.getInstance(getProject());
    List<String> existingNotNulls = new ArrayList<>(notNullManager.getNotNulls());
    existingNotNulls.add("javax.validation.constraints.NotNull");
    notNullManager.setNotNulls(existingNotNulls.toArray(String[]::new));

    doTest();
  }

  // Test with AccessorsPrefix and @NonNull fields
  public void testBuilderWithAccessorsPrefixAndNonNull() {
    doTest();
  }

  // Test with custom builder/build method names and @NonNull fields
  public void testBuilderWithCustomNamesAndNonNull() {
    doTest();
  }

  // Test with @Builder on constructor and @NonNull fields
  public void testBuilderOnConstructorWithNonNull() {
    doTest();
  }

  // Test with @Builder on static method and @NonNull fields
  public void testBuilderOnStaticMethodWithNonNull() {
    doTest();
  }

  // Test with predefined builder class and @NonNull fields
  public void testBuilderWithPredefinedBuilderAndNonNull() {
    doTest();
  }
}