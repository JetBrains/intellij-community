package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

import java.util.List;

public class RedundantModifiersQuickFixTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/inspection/redundantModifierInspection";
  }

  public void testUtilityClassClassWithStaticField() {
    findAccessModifierActions("@UtilityClass already marks fields static.");
  }

  public void testUtilityClassClassWithStaticMethod() {
    findAccessModifierActions("@UtilityClass already marks methods static.");
  }

  public void testUtilityClassClassWithStaticInnerClass() {
    findAccessModifierActions("@UtilityClass already marks inner classes static.");
  }

  public void testValueClassWithPrivateField() {
    findAccessModifierActions("@Value already marks non-static, package-local fields private.");
  }

  public void testValueClassWithFinalField() {
    findAccessModifierActions("@Value already marks non-static fields final.");
  }

  private void findAccessModifierActions(String message) {
    myFixture.configureByFile(getTestName(false) + ".java");

    final List<IntentionAction> availableActions = getAvailableActions();
    assertTrue(message,
               ContainerUtil.exists(availableActions,
                                    action -> action.getText().contains("Change access modifier")));
  }

  protected List<IntentionAction> getAvailableActions() {
    final Editor editor = getEditor();
    final PsiFile file = getFile();
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
    return CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
  }

}
