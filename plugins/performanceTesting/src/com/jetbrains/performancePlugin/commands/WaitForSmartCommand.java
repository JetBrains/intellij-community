package com.jetbrains.performancePlugin.commands;


import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

public final class WaitForSmartCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "waitForSmart";

  /**
   * Wait for 10 seconds after the dumb mode completes.
   * <p/>
   * There are background IDE processes (<backgroundPostStartupActivity/>)
   * that start after 5 seconds of project opening, or in general, at random time.
   * <p/>
   * We would like to maximize the chances that the IDE and the project are fully ready for work,
   * to make our tests predictable and reproducible.
   * <p/>
   * This is not a guaranteed solution: there may be services that schedule background tasks
   * (by means of {@code AppExecutorUtil#getAppExecutorService()}) and it may be hard to know that they all have completed.
   * <p/>
   * TODO: a better place for this awaiting is in the ProjectLoaded test script runner.
   * But for now we use this `waitForSmart` only in indexes-related tests and
   * call it before every "comparing" command (checkIndices/compareIndexes/etc)
   */
  private static final int SMART_MODE_MINIMUM_DELAY = 10000;

  private final Alarm myAlarm = new Alarm();

  public WaitForSmartCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    AsyncPromise<Object> completion = new AsyncPromise<>();
    completeWhenSmartModeIsLongEnough(context.getProject(), completion);
    return completion;
  }

  private void completeWhenSmartModeIsLongEnough(@NotNull Project project, @NotNull AsyncPromise<Object> completion) {
    DumbService.getInstance(project).smartInvokeLater(() -> myAlarm.addRequest(() -> {
      if (DumbService.isDumb(project)) {
        completeWhenSmartModeIsLongEnough(project, completion);
      }
      else {
        completion.setResult(null);
      }
    }, SMART_MODE_MINIMUM_DELAY));
  }
}
