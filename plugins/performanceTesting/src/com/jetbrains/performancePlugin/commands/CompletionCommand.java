package com.jetbrains.performancePlugin.commands;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionPhaseListener;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;


public class CompletionCommand extends PerformanceCommand {

  public static final String NAME = "doComplete";
  public static final String PREFIX = CMD_PREFIX + NAME;
  public static final String SPAN_NAME = "completion";

  private static final Logger LOG = Logger.getInstance(CompletionCommand.class);

  public CompletionCommand(@NotNull String text, int line) {
    super(text, line);
  }

  private CompletionType getCompletionType() {
    String completionTypeArg = getArgument(0);

    switch (completionTypeArg) {
      case "SMART" -> {
        LOG.info(String.format("'%s' was passed as argument, so SMART completion will be used", completionTypeArg));
        return CompletionType.SMART;
      }
      //case "BASIC",
      default -> {
        LOG.info(String.format("'%s' was passed as argument, so BASIC completion will be used", completionTypeArg));
        return CompletionType.BASIC;
      }
    }
  }

  private String getArgument(int index){
    String[] completionArgs = extractCommandArgument(PREFIX).trim().toUpperCase().split(" ");
    if(completionArgs.length > index) {
      return completionArgs[index];
    }
    else {
      return "";
    }
  }

  @Override
  protected String getName() {
    return NAME;
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Disposable listenerDisposable = Disposer.newDisposable();
    Ref<Span> span = new Ref<>();
    Ref<Scope> scope = new Ref<>();
    ApplicationManager.getApplication().getMessageBus().connect(listenerDisposable)
      .subscribe(CompletionPhaseListener.TOPIC, new CompletionPhaseListener() {
        @Override
        public void completionPhaseChanged(boolean isCompletionRunning) {
          if (!isCompletionRunning && !CompletionServiceImpl.isPhase(CompletionPhase.CommittingDocuments.class)) {
            if (CompletionServiceImpl.getCurrentCompletionProgressIndicator() == null) {
              String description =
                "CompletionServiceImpl.getCurrentCompletionProgressIndicator() is null on " + CompletionServiceImpl.getCompletionPhase();
              span.get().setStatus(StatusCode.ERROR, description);
              actionCallback.reject(description);
            }
            else {
              int size = CompletionServiceImpl.getCurrentCompletionProgressIndicator().getLookup().getItems().size();
              span.get().setAttribute("number", size);
              span.get().end();
              scope.get().close();
              context.message("Number of elements: " + size, getLine());
              actionCallback.setDone();
            }
            Disposer.dispose(listenerDisposable);
          }
        }
      });

    ApplicationManager.getApplication().invokeLater(Context.current().wrap(() -> {
      Project project = context.getProject();
      Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      span.set(startSpan(SPAN_NAME));
      scope.set(span.get().makeCurrent());
      new CodeCompletionHandlerBase(getCompletionType(), true, false, true).invokeCompletion(project, editor);
    }));
    return Promises.toPromise(actionCallback);
  }
}
