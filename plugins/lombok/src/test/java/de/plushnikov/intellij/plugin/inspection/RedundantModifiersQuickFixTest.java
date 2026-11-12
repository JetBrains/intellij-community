package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RedundantModifiersQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, "/inspection/redundantModifierInspection");

  @Test
  public void testUtilityClassClassWithStaticField() {
    findAccessModifierActions("@UtilityClass already marks fields static.");
  }

  @Test
  public void testUtilityClassClassWithStaticMethod() {
    findAccessModifierActions("@UtilityClass already marks methods static.");
  }

  @Test
  public void testUtilityClassClassWithStaticInnerClass() {
    findAccessModifierActions("@UtilityClass already marks inner classes static.");
  }

  @Test
  public void testValueClassWithPrivateField() {
    findAccessModifierActions("@Value already marks non-static, package-local fields private.");
  }

  @Test
  public void testValueClassWithFinalField() {
    findAccessModifierActions("@Value already marks non-static fields final.");
  }

  private void findAccessModifierActions(String message) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");

    final List<IntentionAction> availableActions = getAvailableActions();
    assertTrue(ContainerUtil.exists(availableActions,
                                    action -> action.getText().contains("Change access modifier")),
               message);
  }

  protected List<IntentionAction> getAvailableActions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final Editor editor = myFixture.getEditor();
    final PsiFile file = myFixture.getFile();
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
    return CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
  }

}
