package com.jetbrains.performancePlugin.commands;

import com.intellij.diagnostic.telemetry.TraceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.KeyCodeTypeCommand;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ConcurrencyUtil;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerListener;
import com.jetbrains.performancePlugin.utils.DaemonCodeAnalyzerResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Command types text with some delay between typing.
 * Text and delay are being set as parameters.
 * Syntax: %delayType <delay in ms>|<Text to type>
 * Example: %delayType 150|Sample text for typing scenario
 */
public class DelayTypeCommand extends KeyCodeTypeCommand {

  public static final String PREFIX = CMD_PREFIX + "delayType";
  public static final char END_CHAR = '#';
  public static final String SPAN_NAME = "typing";
  public static final String CODE_ANALYSIS_SPAN_NAME = "typingCodeAnalyzing";

  private final ScheduledExecutorService myExecutor = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin delayed type");

  public DelayTypeCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  public @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final AsyncPromise<Object> result = new AsyncPromise<>();

    String input = extractCommandArgument(PREFIX);
    String[] delayText = input.split("\\|");
    final long delay = Integer.parseInt(delayText[0]);
    final String text = delayText[1] + END_CHAR;
    final boolean calculateAnalyzesTime = delayText.length > 2 && Boolean.parseBoolean(delayText[2]);
    Ref<Span> spanRef = new Ref<>();
    Ref<Scope> scopeRef = new Ref<>();
    var connection = context.getProject().getMessageBus().simpleConnect();
    Ref<DaemonCodeAnalyzerResult> job = new Ref<>();
    ApplicationManager.getApplication().executeOnPooledThread(Context.current().wrap(() -> {
      TraceUtil.runWithSpanThrows(PerformanceTestSpan.TRACER, SPAN_NAME, span -> {
        span.addEvent("Finding typing target");
        try {
          Waiter.checkCondition(() -> findTarget(context) != null).await(1, TimeUnit.MINUTES);
        }
        catch (InterruptedException e) {
          span.recordException(e);
          result.setError(e);
          return;
        }

        CountDownLatch allScheduled = new CountDownLatch(1);
        for (int i = 0; i < text.length(); i++) {
          char currentChar = text.charAt(i);
          myExecutor.schedule(() -> ApplicationManager.getApplication().invokeAndWait(Context.current().wrap(
            () -> {
              if (currentChar == END_CHAR) {
                if (calculateAnalyzesTime) {
                  job.set(DaemonCodeAnalyzerListener.INSTANCE.listen(connection, spanRef, scopeRef, 0, null));
                  var spanBuilder = PerformanceTestSpan.TRACER.spanBuilder(CODE_ANALYSIS_SPAN_NAME).setParent(Context.current().with(span));
                  spanRef.set(spanBuilder.startSpan());
                  scopeRef.set(spanRef.get().makeCurrent());
                }
                allScheduled.countDown();
                myExecutor.shutdown();
              }
              else {
                span.addEvent("Calling find target second time in DelayTypeCommand");
                TypingTarget typingTarget = findTarget(context);
                if (typingTarget != null) {
                  span.addEvent("Typing " + currentChar);
                  typingTarget.type(String.valueOf(currentChar));
                }
              }
            })), i * delay, TimeUnit.MILLISECONDS);
        }
        try {
          allScheduled.await();
          myExecutor.awaitTermination(1, TimeUnit.MINUTES);
        }
        catch (InterruptedException e) {
          result.setError(e);
        }
      });

      if (calculateAnalyzesTime) {
        job.get().blockingWaitForComplete();
      }
      result.setResult(null);
    }));

    return result;
  }
}
