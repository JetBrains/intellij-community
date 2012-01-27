package com.intellij.tasks;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskCompletionContributor;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

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

  public void testNumberCompletion() throws Exception {
    configureFile("TEST-0<caret>");
    configureRepository(new LocalTaskImpl("TEST-001", "Test task"), new LocalTaskImpl("TEST-002", "Test task 2"));
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertNotNull(elements);
    assertEquals(2, elements.length);
  }

  public void testKeepOrder() throws Exception {
    configureFile("<caret>");
    configureRepository(new LocalTaskImpl("TEST-002", "Test task 2"), new LocalTaskImpl("TEST-001", "Test task 1"));
    myFixture.complete(CompletionType.BASIC);
    assertEquals(Arrays.asList("TEST-002", "TEST-001"), myFixture.getLookupElementStrings());
  }

  public void testSIOOBE() throws Exception {
    doTest("  <caret>    my", "  TEST-001: Test task    my");
  }

  private void doTest(String text, String after) {
    configureFile(text);
    configureRepository(new LocalTaskImpl("TEST-001", "Test task"));
    myFixture.completeBasic();
    myFixture.checkResult(after);
  }

  private void configureFile(String text) {
    PsiFile psiFile = myFixture.configureByText("test.txt", text);
    Document document = myFixture.getDocument(psiFile);
    TaskCompletionContributor.installCompletion(document, getProject(), null, false);
    document.putUserData(CommitMessage.DATA_CONTEXT_KEY, new MapDataContext());

  }

  private void configureRepository(LocalTaskImpl... tasks) {
    TaskManagerImpl manager = (TaskManagerImpl)TaskManager.getManager(getProject());
    manager.setRepositories(Arrays.asList(new TestRepository(tasks)));
    manager.getState().updateEnabled = false;
  }
}
