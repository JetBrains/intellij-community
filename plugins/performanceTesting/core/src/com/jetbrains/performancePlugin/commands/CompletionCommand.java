// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.CompletionPhaseListener;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupListener;
import com.intellij.codeInsight.lookup.LookupManager;
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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import com.jetbrains.performancePlugin.utils.DataDumper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CompletionCommand extends PerformanceCommand {

  public static final String NAME = "doComplete";
  public static final String PREFIX = CMD_PREFIX + NAME;
  public static final String SPAN_NAME = "completion";
  private static final String DUMP_COMPLETION_ITEMS_DIR = "completion.command.report.dir";

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

  public Editor getEditor(PlaybackContext context) {
    return FileEditorManager.getInstance(context.getProject()).getSelectedTextEditor();
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Disposable listenerDisposable = Disposer.newDisposable();
    Ref<Span> span = new Ref<>();
    Ref<Scope> scope = new Ref<>();
    Ref<Long> completionTimeStarted = new Ref<>();
    AtomicBoolean lookupListenerInited = new AtomicBoolean(false);
    ApplicationManager.getApplication().getMessageBus().connect(listenerDisposable)
      .subscribe(CompletionPhaseListener.TOPIC, new CompletionPhaseListener() {
        @Override
        public void completionPhaseChanged(boolean isCompletionRunning) {
          Editor editor = getEditor(context);
          LookupEx lookup = LookupManager.getActiveLookup(editor);
          if (lookup != null && !lookupListenerInited.get()) {
            lookup.addLookupListener(new LookupListener() {
              @Override
              public void firstElementShown() {
                span.get().setAttribute("firstElementShown", System.currentTimeMillis() - completionTimeStarted.get());
              }
            });
            lookupListenerInited.set(true);
          }
          if (!isCompletionRunning && !CompletionServiceImpl.isPhase(CompletionPhase.CommittingDocuments.class) && !span.isNull()) {
            if (CompletionServiceImpl.getCurrentCompletionProgressIndicator() == null) {
              String description =
                "CompletionServiceImpl.getCurrentCompletionProgressIndicator() is null on " + CompletionServiceImpl.getCompletionPhase();
              span.get().setStatus(StatusCode.ERROR, description);
              actionCallback.reject(description);
            }
            else {
              List<LookupElement> items = CompletionServiceImpl.getCurrentCompletionProgressIndicator().getLookup().getItems();
              int size = items.size();
              span.get().setAttribute("number", size);
              span.get().end();
              scope.get().close();
              context.message("Number of elements: " + size, getLine());
              Path dir = getCompletionItemsDir();
              if (dir != null) {
                dumpCompletionVariants(items, dir);
              }
              actionCallback.setDone();
            }
            Disposer.dispose(listenerDisposable);
          }
        }
      });

    ApplicationManager.getApplication().invokeLater(Context.current().wrap(() -> {
      Project project = context.getProject();
      Editor editor = getEditor(context);
      span.set(startSpan(SPAN_NAME));
      scope.set(span.get().makeCurrent());
      completionTimeStarted.set(System.currentTimeMillis());
      new CodeCompletionHandlerBase(getCompletionType(), true, false, true).invokeCompletion(project, editor);
    }));
    return Promises.toPromise(actionCallback);
  }

  public static @Nullable Path getCompletionItemsDir() {
    String property = System.getProperty(DUMP_COMPLETION_ITEMS_DIR);
    if (property != null) {
      return Paths.get(property);
    }
    return null;
  }

  private void dumpCompletionVariants(List<LookupElement> item, @NotNull Path reportPath) {
    File dir = reportPath.toFile();
    dir.mkdirs();
    File file = new File(dir, createTestReportFilename());
    CompletionItemsReport report = new CompletionItemsReport(ContainerUtil.map(item, CompletionVariant::fromLookUp));
    DataDumper.dump(report, file.toPath());
  }

  private @NotNull String createTestReportFilename() {
    return "completion-" + getCompletionType() + "-" + System.currentTimeMillis() + (isWarmupMode() ? "_warmup" : "") + ".txt";
  }

  public static final class CompletionItemsReport {
    public final int totalNumber;
    public final List<CompletionVariant> items;

    @JsonCreator
    public CompletionItemsReport(
      @JsonProperty("items") @NotNull List<CompletionVariant> items
    ) {
      this.totalNumber = items.size();
      this.items = items;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static final class CompletionVariant {
    @JsonProperty
    private final String name;

    @JsonCreator
    private CompletionVariant(@JsonProperty("name") String name) { this.name = name; }

    public String getName() {
      return name;
    }

    public static CompletionVariant fromLookUp(LookupElement element) {
      return new CompletionVariant(element.getLookupString());
    }
  }
}
