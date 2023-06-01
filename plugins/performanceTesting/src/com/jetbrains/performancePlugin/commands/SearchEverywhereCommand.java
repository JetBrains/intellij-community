package com.jetbrains.performancePlugin.commands;

import com.intellij.platform.diagnostic.telemetry.impl.TraceUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.searcheverywhere.*;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ConcurrencyUtil;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Command simulate calling search everywhere action with a given text and specified tab.
 * Syntax: %searchEverywhere [-tab tab] [-close true][-selectFirst][-type text_to_type]|[text]
 * Example: %searchEverywhere -tab symbol -close|EditorIm
 * Example: %searchEverywhere -tab symbol -type pl -close|EditorIm
 * Example: %searchEverywhere -tab symbol -selectFirst|EditorIm
 */
public class SearchEverywhereCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "searchEverywhere";
  private static final Logger LOG = Logger.getInstance(SearchEverywhereCommand.class);

  private final Options myOptions = new Options();

  public SearchEverywhereCommand(@NotNull String text, int line) {
    super(text, line);
    Args.parse(myOptions, extractCommandArgument(PREFIX).split("\\|")[0].split(" "));
  }

  @SuppressWarnings("BlockingMethodInNonBlockingContext")
  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Project project = context.getProject();

    String input = extractCommandArgument(PREFIX);
    String[] args = input.split("\\|");
    Args.parse(myOptions, args[0].split(" "));
    final String tab = myOptions.tab;
    final String insertText = args.length > 1 ? args[1] : "";

    Ref<String> tabId = new Ref<>();
    switch (tab) {
      case "file" -> tabId.set(FileSearchEverywhereContributor.class.getSimpleName());
      case "class" -> tabId.set(ClassSearchEverywhereContributor.class.getSimpleName());
      case "action" -> tabId.set(ActionSearchEverywhereContributor.class.getSimpleName());
      case "symbol" -> tabId.set(SymbolSearchEverywhereContributor.class.getSimpleName());
      case "all" -> tabId.set(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID);
      default -> throw new RuntimeException("Tab is not set");
    }

    Semaphore typingSemaphore = new Semaphore(1);
    TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, "searchEverywhere", globalSpan -> {
      ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(() -> {
        try {
          Component focusedComponent = IdeFocusManager.findInstance().getFocusOwner();
          DataContext dataContext = DataManager.getInstance().getDataContext(focusedComponent);
          IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
          AnActionEvent actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, dataContext);
          TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, "searchEverywhere_dialog_shown", dialogSpan -> {
            SearchEverywhereManager.getInstance(project).show(tabId.get(), "", actionEvent);
          });
          if (!insertText.isEmpty()) {
            insertText(context.getProject(), insertText, typingSemaphore);
          }
          if (!myOptions.typingText.isEmpty()) {
            typeText(context.getProject(), myOptions.typingText, typingSemaphore);
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }));
      try {
        typingSemaphore.acquire();
        SearchEverywhereUI ui = SearchEverywhereManager.getInstance(project).getCurrentlyShownUI();
        if (myOptions.close) {
          ApplicationManager.getApplication().invokeAndWait(() -> ui.closePopup());
        }
        if (myOptions.selectFirst) {
          WriteAction.runAndWait(() -> {
            ApplicationManager.getApplication().invokeAndWait(() -> ui.selectFirst());
          });
        }
      }
      catch (InterruptedException e) {
        LOG.error(e);
      }
      finally {
        actionCallback.setDone();
      }
    });

    return Promises.toPromise(actionCallback);
  }

  @SuppressWarnings("BlockingMethodInNonBlockingContext")
  private static void insertText(Project project, String insertText, Semaphore typingSemaphore)
    throws InterruptedException {
    SearchEverywhereUI ui = SearchEverywhereManager.getInstance(project).getCurrentlyShownUI();
    Span insertSpan = PerformanceTestSpan.TRACER.spanBuilder("searchEverywhere_items_loaded").startSpan();
    typingSemaphore.acquire();
    //noinspection TestOnlyProblems
    Future<List<Object>> elements = ui.findElementsForPattern(insertText);
    ApplicationManager.getApplication().executeOnPooledThread(Context.current().wrap((Callable<Object>)() -> {
      insertSpan.setAttribute("text", insertText);
      List<Object> result = elements.get();
      insertSpan.end();
      insertSpan.setAttribute("number", result.size());
      typingSemaphore.release();
      return result;
    }));
  }

  @SuppressWarnings("BlockingMethodInNonBlockingContext")
  private static void typeText(Project project, String typingText, Semaphore typingSemaphore) throws InterruptedException {
    SearchEverywhereUI ui = SearchEverywhereManager.getInstance(project).getCurrentlyShownUI();
    typingSemaphore.acquire();
    Document document = ui.getSearchField().getDocument();
    Semaphore oneLetterLock = new Semaphore(1);
    ThreadPoolExecutor typing = ConcurrencyUtil.newSingleThreadExecutor("Performance plugin delayed type");
    Ref<Boolean> isTypingFinished = new Ref<>(false);
    Ref<Span> oneLetterSpan = new Ref<>();
    ui.addSearchListener(new SearchAdapter() {
      @Override
      public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
        oneLetterLock.release();
        oneLetterSpan.get().end();
        if (isTypingFinished.get()) {
          typingSemaphore.release();
          typing.shutdown();
        }
      }
    });
    for (int i = 0; i < typingText.length(); i++) {
      final int index = i;
      typing.execute(Context.current().wrap(() -> {
        try {
          oneLetterLock.acquire();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
        ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(() -> {
          try {
            char currentChar = typingText.charAt(index);
            oneLetterSpan.set(
              PerformanceTestSpan.TRACER.spanBuilder("searchEverywhere_items_loaded").startSpan()
                .setAttribute("text", String.valueOf(currentChar)));
            document.insertString(document.getLength(), String.valueOf(currentChar), null);
            if (index == typingText.length() - 1) {
              isTypingFinished.set(true);
            }
          }
          catch (BadLocationException e) {
            throw new RuntimeException(e);
          }
        }));
      }));
    }
  }

  static class Options {
    @Argument
    String tab = "all";

    @Argument
    Boolean close = false;

    @Argument
    Boolean selectFirst = false;

    @Argument(alias = "type")
    String typingText = "";
  }
}
