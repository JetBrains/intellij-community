package com.jetbrains.performancePlugin;

import com.intellij.diagnostic.AbstractMessage;
import com.intellij.diagnostic.MessagePool;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.diagnostic.VMOptions;
import com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.ide.lightEdit.LightEditorInfo;
import com.intellij.ide.lightEdit.LightEditorListener;
import com.intellij.idea.LoggerFactory;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.InitProjectActivityJavaShim;
import com.intellij.openapi.ui.playback.PlaybackRunner;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.Alarm;
import com.intellij.util.AlarmFactory;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.performancePlugin.commands.OpenProjectCommand;
import com.jetbrains.performancePlugin.commands.TakeScreenshotCommand;
import com.jetbrains.performancePlugin.profilers.ProfilersController;
import io.opentelemetry.context.Context;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "SpellCheckingInspection"})
public final class ProjectLoaded extends InitProjectActivityJavaShim implements ApplicationInitializedListener {
  private static final Logger LOG = Logger.getInstance("PerformancePlugin");

  private static final int TIMEOUT = 500;
  public static final String TEST_SCRIPT_FILE_PATH = System.getProperty("testscript.filename");

  /**
   * If an IDE error occurs and this flag is true, a failure of a TeamCity test will be printed
   * to stdout using TeamCity Service Messages (see {@link #reportTeamCityFailedTestAndBuildProblem(String, String, String)}).
   * The name of the failed test will be inferred from the name of the script file
   * (its name without extension, see {@link #getTeamCityFailedTestName()}).
   */
  private static final boolean MUST_REPORT_TEAMCITY_TEST_FAILURE_ON_IDE_ERROR = Boolean.parseBoolean(
    System.getProperty("testscript.must.report.teamcity.test.failure.on.error", "true")
  );

  /**
   * If an IDE error occurs during test script execution, the IDE will exit.
   * This flag determines whether the status code of the exiting process will be 0 (if false) or 1 (if true).
   */
  private static final boolean MUST_EXIT_PROCESS_WITH_NON_SUCCESS_CODE_ON_IDE_ERROR = Boolean.parseBoolean(
    System.getProperty("testscript.must.exist.process.with.non.success.code.on.ide.error", "false")
  );

  private static final String INDEXING_PROFILER_PREFIX = "%%profileIndexing";
  private static ScheduledExecutorService screenshotExecutor;
  private final Alarm myAlarm = AlarmFactory.getInstance().create();

  private static boolean ourScriptStarted;

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  public void runActivity(@NotNull Project project) {
    if (TEST_SCRIPT_FILE_PATH != null && !ourScriptStarted) {
      ourScriptStarted = true;
      if (System.getProperty("ide.performance.screenshot") != null) {
        registerScreenshotTaking(project, System.getProperty("ide.performance.screenshot"));
      }
      LOG.info("Start Execution");
      PerformanceTestSpan.startSpan();
      Pair<String, List<String>> profilerSettings = initializeProfilerSettingsForIndexing();
      if (profilerSettings != null) {
        try {
          ProfilersController.getInstance().getCurrentProfilerHandler()
            .startProfiling(profilerSettings.first, profilerSettings.second);
        }
        catch (Exception e) {
          System.err.println("Start profile failed: " + e.getMessage());
          ApplicationManagerEx.getApplicationEx().exit(true, true);
        }
      }
      if (OpenProjectCommand.Companion.shouldOpenInSmartMode(project)) {
        runScriptAfterDumb(project);
      }
      else if (SystemProperties.getBooleanProperty("performance.execute.script.after.scanning", false)) {
        runScriptDuringIndexing(project);
      }
      else {
        runScriptFromFile(project);
      }
    }
    else {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.info(PerformanceTestingBundle.message("startup.silent"));
      }
    }
  }

  private static void registerScreenshotTaking(@NotNull Project project, String fileName) {
    screenshotExecutor = ConcurrencyUtil.newSingleScheduledThreadExecutor("Performance plugin screenshoter");
    screenshotExecutor.scheduleWithFixedDelay(()-> {
      TakeScreenshotCommand.takeScreenshotOfFrame(project, fileName);
    }, 0, 1, TimeUnit.MINUTES);
  }

  private static Pair<String, List<String>> initializeProfilerSettingsForIndexing() {
    try {
      List<String> lines = FileUtil.loadLines(getTestFile());
      for (String line : lines) {
        if (line.startsWith(INDEXING_PROFILER_PREFIX)) {
          String[] command = line.substring(INDEXING_PROFILER_PREFIX.length()).trim().split("\\s+", 2);
          String indexingActivity = command[0];
          List<String> profilingParameters = command.length > 1 ? Arrays.asList(command[1].trim().split(","))
                                                                : new ArrayList<>();
          return new Pair<>(indexingActivity, profilingParameters);
        }
      }
    }
    catch (IOException ignored) {
      System.err.println(PerformanceTestingBundle.message("startup.script.read.error"));
      ApplicationManagerEx.getApplicationEx().exit(true, true);
    }
    return null;
  }

  private static File getTestFile() {
    File file = new File(TEST_SCRIPT_FILE_PATH);
    if (!file.isFile()) {
      System.err.println(PerformanceTestingBundle.message("startup.noscript", file.getAbsolutePath()));
      ApplicationManagerEx.getApplicationEx().exit(true, true);
    }

    return file;
  }


  public static void reportErrorsFromMessagePool() {
    MessagePool messagePool = MessagePool.getInstance();
    List<AbstractMessage> ideErrors = messagePool.getFatalErrors(false, true);
    for (AbstractMessage message : ideErrors) {
      try {
        reportScriptError(message);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        message.setRead(true);
      }
    }
  }

  static String generifyErrorMessage(String originalMessage) {
    return originalMessage
      // text@3ba5aac, text => text<ID>, text
      .replaceAll("[$@#][A-Za-z0-9-_]+", "<ID>")

      // java-design-patterns-master.db451f59 => java-design-patterns-master.<HASH>
      .replaceAll("[.]([A-Za-z]+[0-9]|[0-9]+[A-Za-z])[A-Za-z0-9]*", ".<HASH>")

      // 0x01 => <HEX>
      .replaceAll("0x[0-9a-fA-F]+", "<HEX>")

      // text1234text => text<NUM>text
      .replaceAll("[0-9]+", "<NUM>");
  }

  private static void reportScriptError(@NotNull AbstractMessage errorMessage) throws IOException {
    Throwable throwable = errorMessage.getThrowable();

    Throwable cause = throwable;
    String causeMessage = "";
    while (cause.getCause() != null) {
      cause = cause.getCause();
      causeMessage = cause.getMessage();
    }
    if (causeMessage == null || causeMessage.isEmpty()) {
      causeMessage = errorMessage.getMessage();
      if (causeMessage == null || causeMessage.isEmpty()) {
        String throwableMessage = getNonEmptyThrowableMessage(throwable);
        int index = throwableMessage.indexOf("\tat ");
        if (index != -1) {
          causeMessage = throwableMessage.substring(0, index);
        }
        else {
          causeMessage = throwableMessage;
        }
      }
    }

    Path scriptErrorsDir = Paths.get(PathManager.getLogPath(), "script-errors");
    Files.createDirectories(scriptErrorsDir);

    try (Stream<Path> stream = Files.walk(scriptErrorsDir)) {
      String finalCauseMessage = causeMessage;
      boolean isDuplicated = stream.filter(path -> path.getFileName().toString().equals("message.txt"))
        .anyMatch(path -> {
          try {
            return Files.readString(path).equals(finalCauseMessage);
          }
          catch (IOException ex) {
            LOG.error(ex.getMessage());
            return false;
          }
        });
      if (isDuplicated) {
        return;
      }
    }

    for (int i = 1; i < 1000; i++) {
      Path errorDir = scriptErrorsDir.resolve("error-" + i);
      if (Files.exists(errorDir)) {
        continue;
      }
      Files.createDirectories(errorDir);
      Files.writeString(errorDir.resolve("message.txt"), causeMessage);
      Files.writeString(errorDir.resolve("stacktrace.txt"), errorMessage.getThrowableText());

      List<Attachment> attachments = errorMessage.getAllAttachments();
      for (int j = 0; j < attachments.size(); j++) {

        Attachment attachment = attachments.get(j);
        writeAttachmentToErrorDir(attachment, errorDir.resolve(j + "-" + attachment.getName()));
      }
      return;
    }
    LOG.error("Too many errors have been reported during script execution. See " + scriptErrorsDir);
  }

  private static void writeAttachmentToErrorDir(Attachment attachment, Path path) {
    var executor = AppExecutorUtil.getAppScheduledExecutorService();
    var counter = new AtomicInteger(0);
    var isDone = new AtomicBoolean(false);

    Path attachmentPath = Paths.get(attachment.getPath());
    try {
      Files.writeString(path, attachment.getDisplayText(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
      Files.writeString(path, System.lineSeparator(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }
    catch (Exception e) {
      LOG.warn("Failed to write attachment `display text`", e);
    }

    var task = new Runnable() {
      @Override
      public void run() {
        if (isDone.get()) return;

        try (FileChannel channel = FileChannel.open(attachmentPath, StandardOpenOption.READ)) {
          channel.lock();

          int bufferSize = 1024;
          if (bufferSize > channel.size()) {
            bufferSize = (int)channel.size();
          }
          ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

          try (var writer = Files.newByteChannel(path)) {
            while (channel.read(buffer) > 0) {
              writer.write(buffer);
              buffer.clear();
            }
          }

          isDone.set(true);
        }
        catch (Exception e) {
          LOG.warn("Failed to store attachment", e);
        }
        finally {
          if (counter.getAndIncrement() >= 5) {
            LOG.warn(String.format("Unable to store attachment %s %s to path %s",
                                   attachment.getName(), attachment.getPath(), path.toString()));
            isDone.set(true);
          }
        }
      }
    };

    executor.scheduleWithFixedDelay(task, 0, 200, TimeUnit.MILLISECONDS);
  }

  @NotNull
  private static String getNonEmptyThrowableMessage(@NotNull Throwable throwable) {
    if (throwable.getMessage() != null && !throwable.getMessage().isEmpty()) {
      return throwable.getMessage();
    }
    return throwable.getClass().getName();
  }

  @NotNull
  private static String getTeamCityFailedTestName() {
    return FileUtilRt.getNameWithoutExtension(getTestFile().getName());
  }

  private static void reportTeamCityFailedTestAndBuildProblem(@NotNull String testName,
                                                              @NotNull String failureMessage,
                                                              @NotNull String failureDetails) {
    testName = generifyErrorMessage(testName);

    System.out.printf("##teamcity[testFailed name='%s' message='%s' details='%s']\n",
                      encodeStringForTC(testName),
                      encodeStringForTC(failureMessage),
                      encodeStringForTC(failureDetails));

    System.out.printf("##teamcity[buildProblem description='%s' identity='%s'] ",
                      encodeStringForTC(failureMessage),
                      encodeStringForTC(testName));
  }

  private static @NotNull String encodeStringForTC(@NotNull String line) {
    final int MAX_DESCRIPTION_LENGTH = 7_500;
    return line.substring(0, Math.min(MAX_DESCRIPTION_LENGTH, line.length())).
      replaceAll("\\|", "||").
      replaceAll("\\[", "|[").
      replaceAll("]", "|]").
      replaceAll("\n", "|n").
      replaceAll("'", "|'").
      replaceAll("\r", "|r");
  }

  @Override
  public void componentsInitialized() {
    if (ApplicationManagerEx.getApplicationEx().isLightEditMode()) {
      LightEditService.getInstance().getEditorManager().addListener(new LightEditorListener() {
        @Override
        public void afterSelect(@Nullable LightEditorInfo editorInfo) {
          StartUpPerformanceReporter.Companion.logStats("LightEditor");
          runActivity(Objects.requireNonNull(LightEditService.getInstance().getProject()));
        }
      });
    }
  }

  private void runScriptAfterDumb(Project project) {
    DumbService.getInstance(project).smartInvokeLater(Context.current().wrap(() -> {
      myAlarm.addRequest(Context.current().wrap(() -> {
        if (DumbService.isDumb(project) || CoreProgressManager.getCurrentIndicators().size() != 0) {
          runScriptAfterDumb(project);
        }
        else {
          runScriptFromFile(project);
        }
      }), TIMEOUT);
    }));
  }

  private void runScriptDuringIndexing(Project project) {
    ApplicationManager.getApplication().executeOnPooledThread(Context.current().wrap(() -> myAlarm.addRequest(Context.current().wrap(() -> {
      List<ProgressIndicator> indicators = CoreProgressManager.getCurrentIndicators();
      boolean indexingInProgress = false;
      for (ProgressIndicator indicator : indicators) {
        String indicatorText = indicator.getText();
        if (indicatorText != null && indicatorText.contains("Indexing")) {
          indexingInProgress = true;
          break;
        }
      }
      if (indexingInProgress) {
        runScriptFromFile(project);
      }
      else {
        runScriptDuringIndexing(project);
      }
    }), TIMEOUT)));
  }

  public static void runScript(Project project, String script) {
    PlaybackRunner playback = new PlaybackRunnerExtended(script, new CommandLogger(), project);
    ActionCallback scriptCallback = playback.run();
    runScript(scriptCallback, project);
  }

  private static void runScriptFromFile(Project project) {
    PlaybackRunner playback = new PlaybackRunnerExtended("%include " + getTestFile(), new CommandLogger(), project);
    playback.setScriptDir(getTestFile().getParentFile());
    ActionCallback scriptCallback = playback.run();
    CommandsRunner.setStartActionCallback(scriptCallback);
    runScript(scriptCallback, project);
  }

  private static void runScript(ActionCallback scriptCallback, Project project) {
    scriptCallback
      .doWhenDone(() -> {
        LOG.info("Execution of the script has been finished successfully");
      })
      .doWhenRejected(errorMessage -> {
        String message = "IDE will be terminated because some errors are detected while running the startup script: " + errorMessage;
        if (MUST_REPORT_TEAMCITY_TEST_FAILURE_ON_IDE_ERROR) {
          String testName = getTeamCityFailedTestName();
          reportTeamCityFailedTestAndBuildProblem(testName, message, "");
        }

        if (SystemProperties.getBooleanProperty("startup.performance.framework", false)) {
          storeFailureToFile(errorMessage);
        }

        LOG.error(message);

        String threadDump = "Thread dump before IDE termination:\n" + ThreadDumper.dumpThreadsToString();
        LOG.info(threadDump);

        if (System.getProperty("ide.performance.screenshot.on.failure") != null) {
          TakeScreenshotCommand.takeScreenshotOfFrame(project, System.getProperty("ide.performance.screenshot.before.kill"));
        }

        if (MUST_EXIT_PROCESS_WITH_NON_SUCCESS_CODE_ON_IDE_ERROR) {
          System.exit(1);
        }
        else {
          ApplicationManagerEx.getApplicationEx().exit(true, true);
        }
      });
  }

  private static void storeFailureToFile(String errorMessage) {
    try {
      Path logDir = Path.of(PathManager.getLogPath());
      String ideaLogContent = Files.readString(logDir.resolve(LoggerFactory.LOG_FILE_NAME));
      String substringBegin = ideaLogContent.substring(ideaLogContent.indexOf(errorMessage));
      Timestamp timestamp = new Timestamp(System.currentTimeMillis());
      String date = timestamp.toString().substring(0, 10);
      int endIndex = substringBegin.indexOf(date);
      String errorMessageFromLog;
      if (endIndex != -1) {
        errorMessageFromLog = substringBegin.substring(0, endIndex);
      }
      else {
        errorMessageFromLog = substringBegin;
      }
      Path failureCause = logDir.resolve("failure_cause.txt");
      Files.writeString(failureCause, errorMessageFromLog);
    }
    catch (Exception ex) {
      LOG.error(ex.getMessage());
    }
  }

  static final class MyAppLifecycleListener implements AppLifecycleListener {
    MyAppLifecycleListener() {
      if (TEST_SCRIPT_FILE_PATH == null) {
        throw ExtensionNotApplicableException.create();
      }
    }

    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
      MessagePool messagePool = MessagePool.getInstance();
      LOG.info("Error watcher has started");
      messagePool.addListener(ProjectLoaded::reportErrorsFromMessagePool);
    }

    @Override
    public void appClosing() {
      var executor = screenshotExecutor;
      if (executor != null) {
        executor.shutdown();
      }
      PerformanceTestSpan.endSpan();
      reportErrorsFromMessagePool();
    }
  }
}
