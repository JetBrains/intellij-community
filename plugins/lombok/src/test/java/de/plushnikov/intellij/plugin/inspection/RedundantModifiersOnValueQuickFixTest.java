package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

import java.util.List;

import static de.plushnikov.intellij.plugin.inspection.LombokInspectionTest.TEST_DATA_INSPECTION_DIRECTORY;

public class RedundantModifiersOnValueQuickFixTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return TEST_DATA_INSPECTION_DIRECTORY + "/redundantModifierInspection";
  }

  public void testValueClassWithPrivateField() {
    myFixture.configureByFile(getBasePath() + '/' + getTestName(false) + ".java");

    final List<IntentionAction> availableActions = getAvailableActions();
    assertTrue("Redundant private field modifier",
      availableActions.stream().anyMatch(action -> action.getText().contains("Change access modifier")));
  }

  public void testValueClassWithFinalField() {
    myFixture.configureByFile(getBasePath() + '/' + getTestName(false) + ".java");

    final List<IntentionAction> availableActions = getAvailableActions();
    assertTrue("Redundant final field modifier",
      availableActions.stream().anyMatch(action -> action.getText().contains("Change access modifier")));
  }

  protected List<IntentionAction> getAvailableActions() {
    final Editor editor = getEditor();
    final PsiFile file = getFile();
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
    return CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
  }

}
