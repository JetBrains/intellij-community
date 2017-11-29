package com.intellij.tasks;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.CommitMessage;
import com.intellij.psi.PsiFile;
import com.intellij.tasks.actions.TaskAutoCompletionListProvider;
import com.intellij.tasks.impl.LocalTaskImpl;
import com.intellij.tasks.impl.TaskManagerImpl;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.ui.TextFieldWithAutoCompletion;

import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class TaskCompletionTest extends LightCodeInsightFixtureTestCase {

  public TaskCompletionTest() {
    //    super(UsefulTestCase.IDEA_MARKER_CLASS, "PlatformLangXml");
  }

  public void testTaskCompletion() {
    doTest("<caret>", "TEST-001 Test task<caret>");
  }

  public void testPrefix() {
    doTest("TEST-<caret>", "TEST-001 Test task<caret>");
  }

  public void testSecondWord() {
    doTest("my TEST-<caret>", "my TEST-001 Test task<caret>");
  }

  public void testNumberCompletion() {
    configureFile("TEST-0<caret>");
    configureRepository(new LocalTaskImpl("TEST-001", "Test task"), new LocalTaskImpl("TEST-002", "Test task 2"));
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertNotNull(elements);
    assertEquals(2, elements.length);
  }

  public void testKeepOrder() {
    configureFile("<caret>");
    configureRepository(new LocalTaskImpl("TEST-002", "Test task 2"),
                        new LocalTaskImpl("TEST-003", "Test task 1"),
                        new LocalTaskImpl("TEST-001", "Test task 1"),
                        new LocalTaskImpl("TEST-004", "Test task 1")
    );

    getManager().updateIssues(null);
    List<Task> issues = getManager().getCachedIssues();
    //    assertEquals("TEST-002", issues.get(0).getSummary());

    myFixture.complete(CompletionType.BASIC);
    assertEquals(Arrays.asList("TEST-002", "TEST-003", "TEST-001", "TEST-004"), myFixture.getLookupElementStrings());
  }

  public void testSIOOBE() {
    doTest("  <caret>    my", "  TEST-001 Test task    my");
  }

  private void doTest(String text, final String after) {
    configureFile(text);
    final TestRepository repository = configureRepository();
    repository.setTasks(new LocalTaskImpl("TEST-001", "Test task") {
      @Override
      public TaskRepository getRepository() {
        return repository;
      }

      @Override
      public boolean isIssue() {
        return true;
      }
    });
    myFixture.completeBasic();
    myFixture.checkResult(after);
  }

  private void configureFile(String text) {
    PsiFile psiFile = myFixture.configureByText("test.txt", text);
    Document document = myFixture.getDocument(psiFile);
    final Project project = getProject();
    TextFieldWithAutoCompletion.installCompletion(document, project,
                                                  new TaskAutoCompletionListProvider(project),
                                                  false);
    document.putUserData(CommitMessage.DATA_KEY, new CommitMessage(project));
  }

  private TestRepository configureRepository(LocalTaskImpl... tasks) {
    TaskManagerImpl manager = getManager();
    TestRepository repository = new TestRepository(tasks);
    manager.setRepositories(Arrays.asList(repository));
    manager.getState().updateEnabled = false;
    return repository;
  }

  private TaskManagerImpl getManager() {
    return (TaskManagerImpl)TaskManager.getManager(getProject());
  }
}
