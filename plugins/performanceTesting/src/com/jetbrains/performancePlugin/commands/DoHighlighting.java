package com.jetbrains.performancePlugin.commands;

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.Callable;

import static com.intellij.psi.PsiManager.getInstance;

/**
 * Command runs highlighting pass.
 * Runs all highlighting passes. Can't be invoked multiple times in a row since it drops caches before run.
 * Example: %doHighlighting
 */
public final class DoHighlighting extends PerformanceCommand {
  public static final String NAME = "doHighlight";
  public static final String PREFIX = CMD_PREFIX + NAME;

  public static final String SPAN_NAME = "highlighting";
  public static final int MILLIS_IN_NANO = 1000000;

  public DoHighlighting(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected String getName() {
    return NAME;
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(() -> {
      boolean highlightErrorElements = true;
      boolean runAnnotators = true;
      Project project = context.getProject();
      final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor == null) {
        actionCallback.reject("Editor is not open");
        return;
      }
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile == null) {
        actionCallback.reject("Missing PSI file in the editor");
        return;
      }
      getInstance(project).dropPsiCaches();
      ReadAction.nonBlocking(Context.current().wrap((Callable<Void>)() -> {
        long start = System.nanoTime();
        TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, SPAN_NAME, span -> {
          GlobalInspectionTool tool = new HighlightVisitorBasedInspection()
            .setHighlightErrorElements(highlightErrorElements).setRunAnnotators(runAnnotators).setRunVisitors(false);
          InspectionManager inspectionManager = InspectionManager.getInstance(project);
          GlobalInspectionContext globalContext = inspectionManager.createNewGlobalContext();
          InspectionEngine.runInspectionOnFile(psiFile, new GlobalInspectionToolWrapper(tool), globalContext);

          span.setAttribute("lines", editor.getDocument().getLineCount());
          long stop = System.nanoTime();
          span.setAttribute("timeToLines", (double)(stop - start) / MILLIS_IN_NANO / (Math.max(1, editor.getDocument().getLineCount())));
        });
        actionCallback.setDone();
        return null;
      })).wrapProgress(new DaemonProgressIndicator()).submit(AppExecutorUtil.getAppExecutorService());
    }));
    return Promises.toPromise(actionCallback);
  }
}
