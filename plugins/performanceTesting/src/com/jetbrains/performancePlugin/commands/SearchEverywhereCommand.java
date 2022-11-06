package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.searcheverywhere.*;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SearchEverywhereCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "searchEverywhere";
  private static final Logger LOG = Logger.getInstance(SearchEverywhereCommand.class);

  public SearchEverywhereCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Project project = context.getProject();

    String input = extractCommandArgument(PREFIX);
    String[] args = input.split("\\|");
    final String tab = args.length > 0 ? args[0] : "all";
    final String text = args.length > 1 ? args[1] : "";

    Ref<String> tabId = new Ref<>();
    switch (tab) {
      case "file" -> tabId.set(FileSearchEverywhereContributor.class.getSimpleName());
      case "class" -> tabId.set(ClassSearchEverywhereContributor.class.getSimpleName());
      case "action" -> tabId.set(ActionSearchEverywhereContributor.class.getSimpleName());
      case "symbol" -> tabId.set(SymbolSearchEverywhereContributor.class.getSimpleName());
      case "all" -> tabId.set(SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID);
      default -> throw new RuntimeException("Tab is not set");
    }

    Ref<Future<List<Object>>> result = new Ref<>();
    Ref<Span> spanRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(() -> {
      Component focusedComponent = IdeFocusManager.findInstance().getFocusOwner();
      DataContext dataContext = DataManager.getInstance().getDataContext(focusedComponent);
      IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
      AnActionEvent actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_POPUP, null, dataContext);
      SearchEverywhereManager.getInstance(project).show(tabId.get(), "", actionEvent);
      Span span = PerformanceTestSpan.TRACER.spanBuilder("searchEverywhere_" + tab).startSpan();
      spanRef.set(span);
      try (Scope ignored = span.makeCurrent()) {
        Future<List<Object>> resultList = SearchEverywhereManager.getInstance(project).getCurrentlyShownUI().findElementsForPattern(text);
        result.set(resultList);
      }
    }));
    try {
      result.get().get();
    }
    catch (InterruptedException | ExecutionException e) {
      LOG.error(e);
    }
    finally {
      spanRef.get().end();
      actionCallback.setDone();
    }
    return Promises.toPromise(actionCallback);
  }
}
