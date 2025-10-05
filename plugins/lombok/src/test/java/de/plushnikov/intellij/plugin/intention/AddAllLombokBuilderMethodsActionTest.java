package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Predicates;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.processor.handler.BuilderHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests for {@link AddAllLombokBuilderMethodsAction}
 */
public class AddAllLombokBuilderMethodsActionTest extends LombokIntentionActionTest {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/addAllBuilderMethods";
  }

  @Override
  public ModCommandAction getAction() {
    return new AddAllLombokBuilderMethodsAction();
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

  // Test with basic @Builder annotation on class
  public void testBasicBuilder() {
    doTest();
  }

  // Test with @SuperBuilder annotation
  public void testSuperBuilderSimple() {
    doTest();
  }

  // Test with notNull fields
  public void testBuilderWithNotNull() {
    doTest();
  }

  // Test with AccessorsPrefix
  public void testBuilderWithAccessorsPrefix() {
    doTest();
  }

  // Test with custom builder/build method names
  public void testBuilderWithCustomNames() {
    doTest();
  }

  // Test with @Builder on constructor
  public void testBuilderOnConstructor() {
    doTest();
  }

  // Test with @Builder on a static method
  public void testBuilderOnStaticMethod() {
    doTest();
  }

  // Test with a predefined builder class
  public void testBuilderWithPredefinedBuilder() {
    doTest();
  }
}