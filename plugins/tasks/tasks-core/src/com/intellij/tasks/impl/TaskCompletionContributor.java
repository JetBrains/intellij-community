package com.intellij.tasks.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.tasks.Task;
import com.intellij.tasks.actions.TaskSearchSupport;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Dmitry Avdeev
 */
public class TaskCompletionContributor extends CompletionContributor {

  public static void installCompletion(Document document, Project project, Consumer<Task> consumer) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
      //noinspection unchecked
      psiFile.putUserData(KEY, consumer == null ? (Consumer<Task>)Consumer.EMPTY_CONSUMER : consumer);
    }
  }

  private static final Key<Consumer<Task>> KEY = Key.create("task completion available");

  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    final Consumer<Task> consumer = file.getUserData(KEY);
    if (consumer != null) {
      result.stopHere();

      String text = parameters.getOriginalFile().getText();
      int i = text.lastIndexOf(' ', parameters.getOffset() - 1) + 1;
      final String prefix = text.substring(i, parameters.getOffset());
      if (parameters.getInvocationCount() == 0) {                         // is autopopup
        return;
      }
      result = result.withPrefixMatcher(new PlainPrefixMatcher(prefix));

      final TaskSearchSupport searchSupport = new TaskSearchSupport(file.getProject(), false);
      List<Task> items = searchSupport.getItems(prefix, true);
      addCompletionElements(result, consumer, items);

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
            addCompletionElements(result, consumer, tasks);
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

  private static void addCompletionElements(CompletionResultSet result, final Consumer<Task> consumer, List<Task> items) {
    final AutoCompletionPolicy completionPolicy = ApplicationManager.getApplication().isUnitTestMode()
                                                  ? AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE
                                                  : AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
    result.addAllElements(ContainerUtil.map(items, new Function<Task, LookupElement>() {
      @Override
      public LookupElement fun(final Task task) {
        LookupElementBuilder builder = LookupElementBuilder.create(task.getId())
          .setIcon(task.getIcon())
          .addLookupString(task.getSummary())
          .setTailText(" " + task.getSummary(), true)
          .setInsertHandler(new InsertHandler<LookupElement>() {
            @Override
            public void handleInsert(InsertionContext context, LookupElement item) {
              Document document = context.getEditor().getDocument();
              String s = task.getId() + ": " + task.getSummary();
              document.replaceString(context.getStartOffset(), context.getTailOffset(), s);
              context.getEditor().getCaretModel().moveToOffset(context.getStartOffset() + s.length());
              consumer.consume(task);
            }
          });
        if (task.isClosed()) {
          builder = builder.setStrikeout();
        }
        return builder.withAutoCompletionPolicy(completionPolicy);
      }
    }));
  }
}
