package com.jetbrains.performancePlugin.commands;

import com.intellij.diagnostic.telemetry.TraceUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.TypingTarget;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.KeyCodeTypeCommand;
import com.intellij.util.ConcurrencyUtil;
import com.jetbrains.performancePlugin.PerformanceTestSpan;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DelayTypeCommand extends KeyCodeTypeCommand {

  public static final String PREFIX = CMD_PREFIX + "delayType";
  public static final char END_CHAR = '#';
  public static final String SPAN_NAME = "typing";
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
      result.setResult(null);
    }));

    return result;
  }
}
