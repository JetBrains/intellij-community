package com.jetbrains.performancePlugin.commands;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.List;

import static com.intellij.openapi.ui.playback.commands.AlphaNumericTypeCommand.findTarget;
import static com.intellij.psi.PsiManager.getInstance;

@SuppressWarnings("TestOnlyProblems")
public final class DoLocalInspection extends AbstractCommand implements Disposable {
  public static final String PREFIX = CMD_PREFIX + "doLocalInspection";
  public static final String SPAN_NAME = "localInspections";

  public DoLocalInspection(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Project project = context.getProject();
    DumbService.getInstance(project).waitForSmartMode();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      TypingTarget target = findTarget(context);
      if (target == null) {
        //actionCallback.reject("There is no focus in editor");
      }
    });
    if (actionCallback.isRejected()) {
      return Promises.toPromise(actionCallback);
    }
    MessageBusConnection busConnection = project.getMessageBus().connect();
    SpanBuilder span = PerformanceTestSpan.TRACER.spanBuilder(SPAN_NAME).setParent(PerformanceTestSpan.getContext());
    Ref<Span> spanRef = new Ref<>();
    Ref<Scope> scopeRef = new Ref<>();
    busConnection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, new DaemonCodeAnalyzer.DaemonListener() {
      @Override
      public void daemonFinished() {
        DaemonCodeAnalyzerImpl myDaemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        assert editor != null;
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        assert psiFile != null;
        if (myDaemonCodeAnalyzer.isErrorAnalyzingFinished(psiFile)) {
          List<HighlightInfo> errorsOnHighlighting =
            DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), HighlightSeverity.ERROR, project);
          List<HighlightInfo> warningsOnHighlighting =
            DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), HighlightSeverity.WARNING, project);
          List<HighlightInfo> weakWarningsOnHighlighting =
            DaemonCodeAnalyzerImpl.getHighlights(editor.getDocument(), HighlightSeverity.WEAK_WARNING, project);

          StringBuilder finishMessage = new StringBuilder("Local inspections have been finished with: ");

          spanRef.get().setAttribute("Errors", errorsOnHighlighting.size());
          if (errorsOnHighlighting.size() > 0) {
            finishMessage.append("\n").append(new String("Errors: " + errorsOnHighlighting.size()));
          }
          for (HighlightInfo error : errorsOnHighlighting) {
            finishMessage.append("\n").append(new String(error.getText() + ": " + error.getDescription()));
          }

          spanRef.get().setAttribute("Warnings", warningsOnHighlighting.size());
          if (warningsOnHighlighting.size() > 0) {
            finishMessage.append("\n").append(new String("Warnings: " + warningsOnHighlighting.size()));
          }
          for (HighlightInfo warning : warningsOnHighlighting) {
            finishMessage.append("\n").append(new String(warning.getText() + ": " + warning.getDescription()));
          }

          spanRef.get().setAttribute("Weak Warnings", warningsOnHighlighting.size());
          if (weakWarningsOnHighlighting.size() > 0) {
            finishMessage.append("\n").append(new String("Weak Warnings: " + weakWarningsOnHighlighting.size()));
          }
          for (HighlightInfo weakWarning : weakWarningsOnHighlighting) {
            finishMessage.append("\n").append(new String(weakWarning.getText() + ": " + weakWarning.getDescription()));
          }
          spanRef.get().end();
          scopeRef.get().close();
          busConnection.disconnect();
          context.message(finishMessage.toString(), getLine());
          actionCallback.setDone();
        }
      }
    });

    DumbService.getInstance(project).smartInvokeLater(() -> {
      getInstance(project).dropPsiCaches();
      context.message("Local inspections have been started", getLine());
      spanRef.set(span.startSpan());
      scopeRef.set(spanRef.get().makeCurrent());
      DaemonCodeAnalyzer.getInstance(project).restart();
    });

    return Promises.toPromise(actionCallback);
  }

  @Override
  public void dispose() {
  }
}
