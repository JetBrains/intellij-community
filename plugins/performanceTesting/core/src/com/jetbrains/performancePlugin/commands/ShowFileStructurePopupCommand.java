// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.ide.util.FileStructurePopup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import static com.intellij.ide.actions.ViewStructureAction.createPopup;

public final class ShowFileStructurePopupCommand extends AbstractCommand implements Disposable {
  public static final String PREFIX = CMD_PREFIX + "showFileStructureDialog";
  public static final String SPAN_NAME = "FileStructurePopup";

  public ShowFileStructurePopupCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(() -> {
      @NotNull Project project = context.getProject();
      FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor();
      if (fileEditor != null) {
        Span span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).startSpan();
        try (Scope ignored = span.makeCurrent()) {
          final FileStructurePopup popup = createPopup(project, fileEditor);
          if (popup != null) {
            Span spanShow = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME + "#Show").startSpan();
            Span spanFill = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME + "#Fill").startSpan();
            popup.showWithResult().onProcessed(path -> {
              actionCallback.setDone();
              spanFill.end();
              span.end();
            });
            spanShow.end();
          }
          else {
            span.setStatus(StatusCode.ERROR, "File structure popup is null");
            actionCallback.reject("File structure popup is null");
          }
        }
      }
      else {
        actionCallback.reject("File editor is null");
      }
    }));
    return Promises.toPromise(actionCallback);
  }

  @Override
  public void dispose() {
  }
}