package com.jetbrains.performancePlugin.commands;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.intention.impl.CachedIntentions;
import com.intellij.codeInsight.intention.impl.IntentionHintComponent;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.diagnostic.telemetry.TraceUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ShowAltEnter extends AbstractCommand implements Disposable {
  public static final String PREFIX = CMD_PREFIX + "altEnter";
  public static final String SPAN_NAME = "showQuickFixes";

  public ShowAltEnter(@NotNull String text, int line) {
    super(text, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {
    ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    String extractCommandList = extractCommandArgument(PREFIX);
    String[] commandList = extractCommandList.split("\\|");
    final String actionName = commandList[0];
    final boolean invoke = commandList.length == 1 || Boolean.parseBoolean(commandList[1]);
    ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(() -> {
      @NotNull Project project = context.getProject();
      Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (editor != null) {
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile != null) {
          TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, SPAN_NAME, span -> {
            ShowIntentionsPass.IntentionsInfo intentions = ShowIntentionActionsHandler.calcIntentions(project, editor, psiFile);
            if (!actionName.isEmpty()) {
              List<HighlightInfo.IntentionActionDescriptor> combined = new ArrayList<>();
              combined.addAll(intentions.intentionsToShow);
              combined.addAll(intentions.inspectionFixesToShow);
              combined.addAll(intentions.errorFixesToShow);
              //combined.addAll(intentions.guttersToShow);
              combined.addAll(intentions.notificationActionsToShow);
              span.setAttribute("number", combined.size());
              Optional<HighlightInfo.IntentionActionDescriptor>
                singleIntention = combined.stream().filter(s -> s.getAction().getText().startsWith(actionName)).findFirst();
              if (singleIntention.isEmpty()) actionCallback.reject(actionName + " is not found among " + combined);
              if (invoke) singleIntention
                .ifPresent(c -> ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, c.getAction(), c.getAction().getText()));
            }
            if (!invoke || actionName.isEmpty()) {
              CachedIntentions cachedIntentions = CachedIntentions.create(project, psiFile, editor, intentions);
              IntentionHintComponent.showIntentionHint(project, psiFile, editor, true, cachedIntentions);
            }
          });
          actionCallback.setDone();
        }
        else {
          actionCallback.reject("PSI File is null");
        }
      }
      else {
        actionCallback.reject("Editor is not opened");
      }
    }));
    return Promises.toPromise(actionCallback);
  }

  @Override
  public void dispose() {
  }
}
