package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiFile;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public abstract class LombokIntentionActionTest extends AbstractLombokLightCodeInsightTestCase {

  public static final String TEST_DATA_INTENTION_DIRECTORY = "/plugins/lombok/testData/intention";

  @Override
  protected String getBasePath() {
    return TEST_DATA_INTENTION_DIRECTORY;
  }

  public abstract IntentionAction getIntentionAction();

  public abstract boolean wasInvocationSuccessful();

  public void doTest() {
    doTest(true);
  }

  public void doTest(boolean intentionAvailable) {
    PsiFile psiFile = loadToPsiFile(getTestName(false) + ".java");
    IntentionAction intentionAction = getIntentionAction();

    assertEquals("Intention \"" + intentionAction.getFamilyName() + "\" was not available at caret",
                 intentionAvailable, intentionAction.isAvailable(myFixture.getProject(), myFixture.getEditor(), psiFile));

    myFixture.launchAction(intentionAction);

    assertTrue("Intention \"" + intentionAction.getFamilyName() + "\" was not properly invoked",
      wasInvocationSuccessful());
  }
}
