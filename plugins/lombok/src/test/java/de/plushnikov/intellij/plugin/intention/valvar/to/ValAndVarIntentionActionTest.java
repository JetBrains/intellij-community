package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

import static de.plushnikov.intellij.plugin.intention.LombokIntentionActionTest.TEST_DATA_INTENTION_DIRECTORY;

public class ValAndVarIntentionActionTest extends AbstractLombokLightCodeInsightTestCase {

  public static final String EXPLICIT_TO_VAL_VAR_DIRECTORY = TEST_DATA_INTENTION_DIRECTORY + "/valvar/replaceExplicitType";

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR;
  }

  @Override
  protected String getBasePath() {
    return EXPLICIT_TO_VAL_VAR_DIRECTORY;
  }

  public void testValAndVar() {
    loadToPsiFile(getTestName(false) + ".java");
    IntentionAction replaceExplicitTypeWithValIntentionAction = new ReplaceExplicitTypeWithValIntentionAction().asIntention();
    IntentionAction replaceExplicitTypeWithVarIntentionAction = new ReplaceExplicitTypeWithVarIntentionAction().asIntention();
    boolean foundVal = false;
    boolean foundVar = false;
    for (IntentionAction availableIntention : myFixture.getAvailableIntentions()) {
      if (availableIntention.getFamilyName().equals(replaceExplicitTypeWithValIntentionAction.getFamilyName())) {
        foundVal = true;
      }
      if (availableIntention.getFamilyName().equals(replaceExplicitTypeWithVarIntentionAction.getFamilyName())) {
        foundVar = true;
      }
      if (foundVal && foundVar) {
        break;
      }
    }
    assertTrue("Both intention actions should be available", foundVal && foundVar);
  }
}
