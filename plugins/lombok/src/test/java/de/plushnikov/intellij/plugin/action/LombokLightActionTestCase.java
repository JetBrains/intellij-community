package de.plushnikov.intellij.plugin.action;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class LombokLightActionTestCase extends AbstractLombokLightCodeInsightTestCase {
  protected abstract AnAction getAction();

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_NEW_DESCRIPTOR;
  }

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
}
