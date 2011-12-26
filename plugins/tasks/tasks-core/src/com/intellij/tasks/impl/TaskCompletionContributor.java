package com.intellij.tasks.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.tasks.Task;
import com.intellij.tasks.actions.TaskSearchSupport;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Dmitry Avdeev
 */
public class TaskCompletionContributor extends CompletionContributor {

  private static final Key<Consumer<Task>> KEY = Key.create("task completion available");
  private static final Key<Boolean> AUTO_POPUP_KEY = Key.create("task completion auto-popup");

  public static void installCompletion(Document document, Project project, @Nullable Consumer<Task> consumer, boolean autoPopup) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
      //noinspection unchecked
      psiFile.putUserData(KEY, consumer == null ? (Consumer<Task>)Consumer.EMPTY_CONSUMER : consumer);
      psiFile.putUserData(AUTO_POPUP_KEY, autoPopup);
    }
  }

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    final Consumer<Task> consumer = file.getUserData(KEY);

    if (CompletionService.getCompletionService().getAdvertisementText() == null) {
      final String shortcut = getActionShortcut(IdeActions.ACTION_QUICK_JAVADOC);
      if (shortcut != null) {
        CompletionService.getCompletionService().setAdvertisementText("Pressing " + shortcut + " would show task description and comments");
      }
    }

    if (consumer != null) {

      String text = parameters.getOriginalFile().getText();
      int i = text.lastIndexOf(' ', parameters.getOffset() - 1) + 1;
      final String prefix = text.substring(i, parameters.getOffset());
      if (parameters.getInvocationCount() == 0 && !file.getUserData(AUTO_POPUP_KEY)) {   // is autopopup
        return;
      }
      result = result.withPrefixMatcher(new PlainPrefixMatcher(prefix));

      final TaskSearchSupport searchSupport = new TaskSearchSupport(file.getProject());
      List<Task> items = searchSupport.getItems(prefix, true);
      addCompletionElements(result, consumer, items, -10000);

      Future<List<Task>> future = ApplicationManager.getApplication().executeOnPooledThread(new Callable<List<Task>>() {
        @Override
        public List<Task> call() {
          return searchSupport.getItems(prefix, false);
        }
      });

      while (true) {
        try {
          List<Task> tasks = future.get(100, TimeUnit.MILLISECONDS);
          if (tasks != null) {
            addCompletionElements(result, consumer, tasks, 0);
            return;
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception ignore) {

        }
        ProgressManager.checkCanceled();
      }
    }
  }

  private static void addCompletionElements(CompletionResultSet result, final Consumer<Task> consumer, List<Task> items, int index) {
    final AutoCompletionPolicy completionPolicy = ApplicationManager.getApplication().isUnitTestMode()
                                                  ? AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE
                                                  : AutoCompletionPolicy.NEVER_AUTOCOMPLETE;

    for (final Task task : items) {
      LookupElementBuilder builder = LookupElementBuilder.create(task, task.getId())
        .setIcon(task.getIcon())
        .addLookupString(task.getSummary())
        .setTailText(" " + task.getSummary(), true)
        .setInsertHandler(new InsertHandler<LookupElement>() {
          @Override
          public void handleInsert(InsertionContext context, LookupElement item) {
            Document document = context.getEditor().getDocument();
            String s = task.getId() + ": " + task.getSummary();
            s = StringUtil.convertLineSeparators(s);
            document.replaceString(context.getStartOffset(), context.getTailOffset(), s);
            context.getEditor().getCaretModel().moveToOffset(context.getStartOffset() + s.length());
            consumer.consume(task);
          }
        });
      if (task.isClosed()) {
        builder = builder.setStrikeout();
      }

      result.addElement(PrioritizedLookupElement.withGrouping(builder.withAutoCompletionPolicy(completionPolicy), index--));
    }
  }
}
