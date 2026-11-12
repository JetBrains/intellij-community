package de.plushnikov.intellij.plugin.intention;

import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixture of the Lombok intention tests. Create it with {@code lombokIntentionFixture}.
 */
@TestOnly
public final class LombokIntentionFixture {

  public static final String TEST_DATA_INTENTION_DIRECTORY = "/intention";

  private final @NotNull LightJavaCodeInsightTestFixture myFixture;
  private final @NotNull Supplier<? extends ModCommandAction> myAction;
  private final @NotNull BooleanSupplier myInvocationCheck;

  public LombokIntentionFixture(@NotNull LightJavaCodeInsightTestFixture fixture,
                                @NotNull Supplier<? extends ModCommandAction> action,
                                @NotNull BooleanSupplier invocationCheck) {
    myFixture = fixture;
    myAction = action;
    myInvocationCheck = invocationCheck;
  }

  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return myFixture;
  }

  public void doTest() {
    doTest(true);
  }

  /**
   * Loads {@code <test name>.java}, checks that the intention is available at the caret when {@code intentionAvailable} is true,
   * runs it, and checks the result with the invocation check of the fixture. The check runs on the EDT.
   */
  public void doTest(boolean intentionAvailable) {
    LombokTestUtil.loadToPsiFile(myFixture, myFixture.getTestName(false) + ".java");
    ModCommandAction intentionAction = myAction.get();

    boolean isActuallyAvailable = ReadAction.computeBlocking(() -> intentionAction.getPresentation(myFixture.getActionContext()) != null);
    assertEquals(intentionAvailable, isActuallyAvailable,
                 "Intention \"" + intentionAction.getFamilyName() + "\" was not available at caret");

    if (isActuallyAvailable) {
      myFixture.launchAction(intentionAction.asIntention());
    }

    assertTrue(EdtTestUtil.runInEdtAndGet(myInvocationCheck::getAsBoolean),
               "Intention \"" + intentionAction.getFamilyName() + "\" was not properly invoked");
  }
}
