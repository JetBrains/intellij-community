package de.plushnikov.intellij.plugin.extension;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

import java.util.List;

public class SneakyThrowsExceptionAddQuickFixTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/extension";
  }

  public void testCheckedExeptionQuickFixExample() {
    myFixture.configureByFile('/' + getTestName(false) + ".java");

    final List<IntentionAction> availableActions = getAvailableActions();
    assertTrue("Intention to add @SneakyThrows was not presented",
               ContainerUtil.exists(availableActions, action -> action.getText().contains("@SneakyThrows")));
  }

  public void testCheckedMultipleExceptionQuickFixExample() {
    myFixture.configureByFile( '/' + getTestName(false) + ".java");

    final List<IntentionAction> availableActions = getAvailableActions();
    assertTrue("Intention to add @SneakyThrows was not presented",
               ContainerUtil.exists(availableActions, action -> action.getText().contains("@SneakyThrows")));
  }

  protected List<IntentionAction> getAvailableActions() {
    final Editor editor = getEditor();
    final PsiFile file = getFile();
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, new int[0], false);
    return CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
  }

}
