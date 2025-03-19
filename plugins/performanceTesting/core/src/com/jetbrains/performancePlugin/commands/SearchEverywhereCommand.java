package com.jetbrains.performancePlugin.commands;

import com.intellij.find.impl.TextSearchContributor;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.searcheverywhere.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.util.ConcurrencyUtil;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

import static com.intellij.ide.actions.searcheverywhere.statistics.SearchFieldStatisticsCollector.wrapDataContextWithActionStartData;
import static com.intellij.openapi.ui.playback.commands.ActionCommand.getInputEvent;
import static com.intellij.openapi.ui.playback.commands.AlphaNumericTypeCommand.findTarget;

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
    getArgs();
  }

  private Boolean isWarmupMode() {
    return extractCommandArgument(CMD_PREFIX).contains("WARMUP");
  }

  private Boolean isStartThoughAction() {
    return extractCommandArgument(CMD_PREFIX).contains("START_THROUGH_ACTION");
  }

  @SuppressWarnings("BlockingMethodInNonBlockingContext")
  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    String[] args = getArgs();
    final String tab = myOptions.tab;
    final String insertText = args.length > 1 ? args[1] : "";

    if (isStartThoughAction()) {
      return executeStartingThroughAction(context, insertText);
    }

    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Project project = context.getProject();

    boolean warmup = isWarmupMode();

    Ref<String> tabId = computeTabId(tab);

    int numberOfPermits = getNumberOfPermits(insertText);
    Semaphore typingSemaphore = new Semaphore(numberOfPermits);
    TraceKt.use(PerformanceTestSpan.getTracer(warmup).spanBuilder("searchEverywhere"), globalSpan -> {
      ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(() -> {
        try {
          TypingTarget target = findTarget(context);
          Component component;
          if (!(target instanceof EditorComponentImpl)) {
            LOG.info("Editor is not opened, focus owner will be used.");
            component = IdeFocusManager.getInstance(project).getFocusOwner();
          }
          else {
            component = (EditorComponentImpl)target;
          }
          DataContext dataContext = CustomizedDataContext.withSnapshot(
            DataManager.getInstance().getDataContext(component),
            sink -> sink.set(CommonDataKeys.PROJECT, context.getProject()));
          DataContext wrappedDataContext = wrapDataContextWithActionStartData(dataContext);
          IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
          TraceKt.use(PerformanceTestSpan.getTracer(warmup).spanBuilder("searchEverywhere_dialog_shown"), dialogSpan -> {
            var manager = SearchEverywhereManager.getInstance(project);
            AnActionEvent event = AnActionEvent.createEvent(
              wrappedDataContext, null, ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP, null);
            manager.show(tabId.get(), "", event);
            attachSearchListeners(manager.getCurrentlyShownUI());
            return null;
          });
          typeOrInsertText(context, insertText, typingSemaphore, warmup);
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
      return null;
    });

    return Promises.toPromise(actionCallback);
  }

  private static @NotNull Ref<String> computeTabId(String tab) {
    Ref<String> tabId = new Ref<>();
    switch (tab) {
      case "text" -> tabId.set(TextSearchContributor.class.getSimpleName());
      case "file" -> tabId.set(FileSearchEverywhereContributor.class.getSimpleName());
      case "class" -> tabId.set(ClassSearchEverywhereContributor.class.getSimpleName());
      case "action" -> tabId.set(ActionSearchEverywhereContributor.class.getSimpleName());
      case "symbol" -> tabId.set(SymbolSearchEverywhereContributor.class.getSimpleName());
      case "all" -> tabId.set(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID);
      default -> throw new RuntimeException("Tab is not set");
    }
    LOG.info(tabId.get());
    return tabId;
  }

  private @NotNull Promise<Object> executeStartingThroughAction(@NotNull PlaybackContext context, String insertText) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Project project = context.getProject();
    boolean warmup = isWarmupMode();

    String actionId = computeActionId();

    final ActionManager am = ActionManager.getInstance();
    final AnAction targetAction = am.getAction(actionId);
    if (targetAction == null) {
      throw new RuntimeException("Unknown action: " + actionId);
    }
    final InputEvent input = getInputEvent(actionId);

    int numberOfPermits = getNumberOfPermits(insertText);
    Semaphore typingSemaphore = new Semaphore(numberOfPermits);
    TraceKt.use(PerformanceTestSpan.getTracer(warmup).spanBuilder("searchEverywhereFromAction"), globalSpan -> {
      context.getRobot().delay(Registry.intValue("actionSystem.playback.delay"));
      ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(() -> {
        try {
          am.tryToExecute(targetAction, input, null, null, false).doWhenProcessed(() -> {//AWT
            SearchEverywhereUI ui = SearchEverywhereManager.getInstance(project).getCurrentlyShownUI();
            attachSearchListeners(ui);

            ApplicationManager.getApplication().invokeAndWait(() -> {
              typeOrInsertText(context, insertText, typingSemaphore, warmup);
            });

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
              closePopupOrSelectFirst(typingSemaphore, ui, actionCallback);
            });
          });
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }));
      return null;
    });

    return Promises.toPromise(actionCallback);
  }

  private void closePopupOrSelectFirst(Semaphore typingSemaphore, SearchEverywhereUI ui, ActionCallback actionCallback) {
    try {
      typingSemaphore.acquire();
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
  }

  private void typeOrInsertText(@NotNull PlaybackContext context, String insertText, Semaphore typingSemaphore, boolean warmup) {
    if (!insertText.isEmpty()) {
      insertText(context.getProject(), insertText, typingSemaphore, warmup);
    }
    if (!myOptions.typingText.isEmpty()) {
      typeText(context.getProject(), myOptions.typingText, typingSemaphore, warmup);
    }
  }

  private @NotNull @Language("devkit-action-id") String computeActionId() {
    String actionId;
    switch (myOptions.tab) {
      case "text" -> actionId = "TextSearchAction";
      case "file" -> actionId = "GotoFile";
      case "class" -> actionId = "GotoClass";
      case "action" -> actionId = "GotoAction";
      case "symbol" -> actionId = "GotoSymbol";
      case "all" -> actionId = "SearchEverywhere";
      default -> throw new RuntimeException("Tab is not set, can't run the action");
    }
    return actionId;
  }

  private int getNumberOfPermits(String insertText) {
    int numberOfPermits;
    if (insertText.isEmpty() && myOptions.typingText.isEmpty()) {
      numberOfPermits = 1; //we don't wait for any text insertion
    }
    else if (!insertText.isEmpty() && !myOptions.typingText.isEmpty()) {
      numberOfPermits = -1; //we wait till both operations are finished
    }
    else {
      numberOfPermits = 0; //we wait till one operation is finished
    }
    return numberOfPermits;
  }

  protected String @NotNull [] getArgs() {
    return getArgs(PREFIX);
  }

  protected String @NotNull [] getArgs(String prefix) {
    String input = extractCommandArgument(prefix);
    String[] args = input.split("\\|");
    Args.parse(myOptions, args[0].split(" "), false);
    return args;
  }

  @SuppressWarnings("BlockingMethodInNonBlockingContext")
  private void insertText(Project project, String insertText, Semaphore typingSemaphore, boolean warmup) {
    SearchEverywhereUI ui = SearchEverywhereManager.getInstance(project).getCurrentlyShownUI();
    Span insertSpan = PerformanceTestSpan.getTracer(warmup).spanBuilder("searchEverywhere_items_loaded").startSpan();
    Span firstBatchAddedSpan = PerformanceTestSpan.getTracer(warmup).spanBuilder("searchEverywhere_first_elements_added").startSpan();
    ui.addSearchListener(new SearchAdapter() {
      @Override
      public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
        super.elementsAdded(list);
        firstBatchAddedSpan.setAttribute("number", list.size());
        firstBatchAddedSpan.end();
      }
    });
    //noinspection TestOnlyProblems
    Future<List<Object>> elements = ui.findElementsForPattern(insertText);
    ApplicationManager.getApplication().executeOnPooledThread(Context.current().wrap((Callable<Object>)() -> {
      insertSpan.setAttribute("text", insertText);
      List<Object> result = elements.get();
      insertSpan.setAttribute("number", result.size());
      insertSpan.end();
      typingSemaphore.release();
      return result;
    }));
  }

  @SuppressWarnings("BlockingMethodInNonBlockingContext")
  private void typeText(Project project, String typingText, Semaphore typingSemaphore, boolean warmup) {
    SearchEverywhereUI ui = SearchEverywhereManager.getInstance(project).getCurrentlyShownUI();
    Document document = ui.getSearchField().getDocument();
    Semaphore oneLetterLock = new Semaphore(1);
    ThreadPoolExecutor typing = ConcurrencyUtil.newSingleThreadExecutor("Performance plugin delayed type");
    Ref<Boolean> isTypingFinished = new Ref<>(false);
    Ref<Span> oneLetterSpan = new Ref<>();
    Ref<Span> firstBatchAddedSpan = new Ref<>();
    ui.addSearchListener(new SearchAdapter() {
      @Override
      public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
        firstBatchAddedSpan.get().setAttribute("number", list.size());
        firstBatchAddedSpan.get().end();
      }

      @Override
      public void searchFinished(@NotNull List<Object> items) {
        super.searchFinished(items);
        oneLetterLock.release();
        if (!oneLetterSpan.isNull()) {
          oneLetterSpan.get().setAttribute("number", items.size());
          oneLetterSpan.get().end();
        }
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
              PerformanceTestSpan.getTracer(warmup).spanBuilder("searchEverywhere_items_loaded").startSpan()
                .setAttribute("text", String.valueOf(currentChar)));
            firstBatchAddedSpan.set(PerformanceTestSpan.getTracer(warmup).spanBuilder("searchEverywhere_first_elements_added").startSpan());
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

  protected void attachSearchListeners(@NotNull SearchEverywhereUI ui) { }

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
