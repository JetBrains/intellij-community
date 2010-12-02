package com.intellij.tasks;

import com.intellij.psi.PsiFile;
import com.intellij.tasks.impl.TaskCompletionContributor;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.Consumer;

import java.util.Arrays;

/**
 * @author Dmitry Avdeev
 */
public class TaskCompletionTest extends LightCodeInsightFixtureTestCase {

  public void testTaskCompletion() throws Exception {
    doTest("<caret>", "TEST-001: Test task<caret>");
  }

  public void testPrefix() throws Exception {
    doTest("TEST-<caret>", "TEST-001: Test task<caret>");
  }

  public void testSecondWord() throws Exception {
    doTest("my TEST-<caret>", "my TEST-001: Test task<caret>");
  }

  public void testSIOOBE() throws Exception {
    doTest("  <caret>    my ", "  TEST-001: Test task    my");
  }

  private void doTest(String text, String after) {
    PsiFile psiFile = myFixture.configureByText("test.txt", text);
    TaskCompletionContributor.installCompletion(myFixture.getDocument(psiFile), getProject(), new Consumer<Task>() {
      @Override
      public void consume(Task task) {

      }
    });
    TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(getProject());
    manager.setRepositories(Arrays.asList(new TestRepository()));
    manager.getState().updateEnabled = false;
    myFixture.completeBasic();
    myFixture.checkResult(after);
  }
}
