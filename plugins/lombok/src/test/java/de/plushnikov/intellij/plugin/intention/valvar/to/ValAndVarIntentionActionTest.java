package de.plushnikov.intellij.plugin.intention.valvar.to;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static de.plushnikov.intellij.plugin.intention.LombokIntentionFixture.TEST_DATA_INTENTION_DIRECTORY;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ValAndVarIntentionActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA_1_8_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lombokCodeInsightFixture(projectFixture, EXPLICIT_TO_VAL_VAR_DIRECTORY);

  public static final String EXPLICIT_TO_VAL_VAR_DIRECTORY = TEST_DATA_INTENTION_DIRECTORY + "/valvar/replaceExplicitType";

  @Test
  public void testValAndVar() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    LombokTestUtil.loadToPsiFile(myFixture, myFixture.getTestName(false) + ".java");
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
    assertTrue(foundVal && foundVar, "Both intention actions should be available");
  }
}
