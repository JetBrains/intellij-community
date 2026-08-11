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
import com.intellij.testFramework.PlatformTestUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.jetbrains.concurrency.Promise;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

public abstract class LombokLightActionTestCase extends AbstractLombokLightCodeInsightTestCase {
  protected abstract AnAction getAction();

  protected void doTest() throws Exception {
    myFixture.configureByFile("/before" + getTestName(false) + ".java");
    performActionTest();
    myFixture.checkResultByFile("/after" + getTestName(false) + ".java", true);
  }

  private void performActionTest() throws TimeoutException, ExecutionException {
    AnAction anAction = getAction();

    Promise<DataContext> contextResult = DataManager.getInstance().getDataContextFromFocusAsync();
    AnActionEvent anActionEvent = AnActionEvent.createEvent(contextResult.blockingGet(10, TimeUnit.SECONDS),
                                                            anAction.getTemplatePresentation().clone(), "", ActionUiKind.NONE, null);
    anAction.actionPerformed(anActionEvent);
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  protected void performProjectViewActionTest(VirtualFile... files) {
    AnAction anAction = getAction();
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, getProject())
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, files)
      .build();
    AnActionEvent anActionEvent = AnActionEvent.createEvent(dataContext, anAction.getTemplatePresentation().clone(), "", ActionUiKind.NONE, null);
    anAction.actionPerformed(anActionEvent);
  }

  protected void waitForProjectViewActionProcessing(BooleanSupplier condition) {
    PlatformTestUtil.waitWithEventsDispatching("Project-view Lombok action did not finish", () -> {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      FileDocumentManager.getInstance().saveAllDocuments();
      return condition.getAsBoolean();
    }, 10);
  }
}
