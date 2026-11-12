package de.plushnikov.intellij.plugin.action;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.Promise;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The fixture of the Lombok action tests. Create it with {@code lombokActionFixture}.
 * <p>
 * The members run the action of the fixture on the EDT.
 */
@TestOnly
public final class LombokActionFixture {
  private final @NotNull LightJavaCodeInsightTestFixture myFixture;
  private final @NotNull Supplier<? extends AnAction> myAction;

  public LombokActionFixture(@NotNull LightJavaCodeInsightTestFixture fixture, @NotNull Supplier<? extends AnAction> action) {
    myFixture = fixture;
    myAction = action;
  }

  public @NotNull LightJavaCodeInsightTestFixture getFixture() {
    return myFixture;
  }

  public @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return myFixture.getTestName(lowercaseFirstLetter);
  }

  /**
   * Configures {@code before<test name>.java}, runs the action with the data context of the focus,
   * and compares the file with {@code after<test name>.java}.
   */
  public void doTest() throws Exception {
    myFixture.configureByFile("/before" + getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      performActionTest();
      myFixture.checkResultByFile("/after" + getTestName(false) + ".java", true);
    });
  }

  private void performActionTest() throws Exception {
    AnAction anAction = myAction.get();

    Promise<DataContext> contextResult = DataManager.getInstance().getDataContextFromFocusAsync();
    AnActionEvent anActionEvent = AnActionEvent.createEvent(contextResult.blockingGet(10, TimeUnit.SECONDS),
                                                            anAction.getTemplatePresentation().clone(), "", ActionUiKind.NONE, null);
    anAction.actionPerformed(anActionEvent);
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  /**
   * Runs the action with the data context of the project view, where {@code files} are selected.
   */
  public void performProjectViewActionTest(VirtualFile... files) {
    AnAction anAction = myAction.get();
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, myFixture.getProject())
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files)
      .build();
    AnActionEvent anActionEvent = AnActionEvent.createEvent(dataContext, anAction.getTemplatePresentation().clone(), "", ActionUiKind.NONE, null);
    EdtTestUtil.runInEdtAndWait(() -> anAction.actionPerformed(anActionEvent));
  }

  /**
   * Waits on the EDT until the background work of a project view action is done and {@code condition} is true.
   */
  public void waitForProjectViewActionProcessing(BooleanSupplier condition) {
    EdtTestUtil.runInEdtAndWait(() -> PlatformTestUtil.waitWithEventsDispatching("Project-view Lombok action did not finish", () -> {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      FileDocumentManager.getInstance().saveAllDocuments();
      return condition.getAsBoolean();
    }, 10));
  }
}
