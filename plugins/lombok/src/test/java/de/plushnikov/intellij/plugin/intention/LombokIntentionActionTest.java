package de.plushnikov.intellij.plugin.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiFile;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public abstract class LombokIntentionActionTest extends AbstractLombokLightCodeInsightTestCase {

  public static final String TEST_DATA_INTENTION_DIRECTORY = "testData/intention";

  @Override
  protected String getBasePath() {
    return TEST_DATA_INTENTION_DIRECTORY;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());
  }

  public abstract IntentionAction getIntentionAction();

  public abstract boolean wasInvocationSuccessful();

  public void doTest() {
    PsiFile psiFile = loadToPsiFile(getTestName(false) + ".java");
    IntentionAction intentionAction = getIntentionAction();
    assertTrue("Intention \"" + intentionAction.getFamilyName() + "\" was not found in file",
      myFixture.getAvailableIntentions().stream().anyMatch(action -> action.getFamilyName().equals(intentionAction.getFamilyName())));
    assertTrue("Intention \"" + intentionAction.getFamilyName() + "\" was not available at caret",
      intentionAction.isAvailable(myFixture.getProject(), myFixture.getEditor(), psiFile));
    myFixture.launchAction(intentionAction);
    assertTrue("Intention \"" + intentionAction.getFamilyName() + "\" was not properly invoked",
      wasInvocationSuccessful());
  }
}
