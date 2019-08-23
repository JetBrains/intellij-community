/* ==========================================================================
 * Copyright 2006 Mevenide Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * =========================================================================
 */


package org.jetbrains.idea.maven.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Consumer;
import com.intellij.util.io.BaseDataReader;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.BuildViewMavenConsole;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenSpyOutputParser;
import org.jetbrains.idea.maven.project.MavenConsole;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.server.MavenServerConsole;

import java.util.function.BiConsumer;

/**
 * @deprecated external executor should work through maven run configuration
 */
@Deprecated
public class MavenExternalExecutor extends MavenExecutor {

  private OSProcessHandler myProcessHandler;

  @NonNls private static final String PHASE_INFO_REGEXP = "\\[INFO\\] \\[.*:.*\\]";
  @NonNls private static final int INFO_PREFIX_SIZE = "[INFO] ".length();

  private JavaParameters myJavaParameters;
  private ExecutionException myParameterCreationError;



  public MavenExternalExecutor(Project project,
                               @NotNull MavenRunnerParameters parameters,
                               @Nullable MavenGeneralSettings coreSettings,
                               @Nullable MavenRunnerSettings runnerSettings,
                               @NotNull MavenConsole console) {
    super(parameters, RunnerBundle.message("external.executor.caption"), console);

    try {
      myJavaParameters = MavenExternalParameters.createJavaParameters(project, myParameters, coreSettings, runnerSettings, null);
    }
    catch (ExecutionException e) {
      myParameterCreationError = e;
    }
  }

  @Override
  public boolean execute(final ProgressIndicator indicator) {
    displayProgress();

    try {
      if (myParameterCreationError != null) {
        throw myParameterCreationError;
      }

      myProcessHandler =
        new MyLineSplittingProcessHandler(myJavaParameters.toCommandLine(), myConsole, line -> updateProgress(indicator, line));

      myConsole.attachToProcess(myProcessHandler);
    }
    catch (ExecutionException e) {
      myConsole.systemMessage(MavenServerConsole.LEVEL_FATAL, RunnerBundle.message("external.startup.failed", e.getMessage()), null);
      return false;
    }

    start();
    readProcessOutput();
    stop();

    return printExitSummary();
  }

  @Override
  void stop() {
    if (myProcessHandler != null) {
      myProcessHandler.destroyProcess();
      myProcessHandler.waitFor();
      setExitCode(myProcessHandler.getProcess().exitValue());
    }
    super.stop();
  }

  private void readProcessOutput() {
    myProcessHandler.startNotify();
    myProcessHandler.waitFor();
  }

  private void updateProgress(@Nullable final ProgressIndicator indicator, final String text) {
    if (indicator != null) {
      if (indicator.isCanceled()) {
        if (!isCancelled()) {
          ApplicationManager.getApplication().invokeLater(() -> cancel());
        }
      }
      if (text.matches(PHASE_INFO_REGEXP)) {
        indicator.setText2(text.substring(INFO_PREFIX_SIZE));
      }
    }
  }

  private static class MyLineSplittingProcessHandler extends OSProcessHandler {
    private final MavenSpyEventsBuffer mySpyEventsBuffer;
    private final MavenSimpleConsoleEventsBuffer mySimpleConsoleEventsBuffer;
    private final @NotNull MavenConsole myConsole;
    private final AnsiEscapeDecoder myDecoder = new AnsiEscapeDecoder();
    @Nullable private final Consumer<String> myProgressConsumer;

    MyLineSplittingProcessHandler(@NotNull GeneralCommandLine commandLine,
                                  @NotNull MavenConsole console,
                                  @Nullable Consumer<String> progressConsumer
    ) throws ExecutionException {
      super(commandLine);
      myProgressConsumer = progressConsumer;
      this.myConsole = console;

      mySpyEventsBuffer = new MavenSpyEventsBuffer((line, key) -> {
        sendToMavenEventParser(line, key);
        if (myProgressConsumer != null) {
          myProgressConsumer.consume(line);
        }
      });
      mySimpleConsoleEventsBuffer = new MavenSimpleConsoleEventsBuffer((line, key) -> printSimpleOutput(line, key), Registry
        .is("maven.spy.events.debug"));
    }

    @Override
    public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
      mySpyEventsBuffer.addText(text, outputType);
      mySimpleConsoleEventsBuffer.addText(text, outputType);
    }


    private void sendToMavenEventParser(@NotNull String text, @NotNull Key outputType) {
      if (myConsole instanceof BuildViewMavenConsole) {
        ((BuildViewMavenConsole)myConsole).sendToEventParser(text, outputType);
      }
    }

    private void printSimpleOutput(@NotNull String text, @NotNull Key outputType) {
      if (!myConsole.isSuppressed(text)) {
        myDecoder.escapeText(text, outputType, (t, ot) -> super.notifyTextAvailable(t, ot));
      }
    }

    @NotNull
    @Override
    protected BaseOutputReader.Options readerOptions() {
      return new BaseOutputReader.Options() {
        @Override
        public BaseDataReader.SleepingPolicy policy() {
          return BaseDataReader.SleepingPolicy.BLOCKING;
        }

        @Override
        public boolean splitToLines() {
          return true;
        }

        @Override
        public boolean sendIncompleteLines() {
          return true;
        }
      };
    }
  }

  /**
   * Expect to receive onle one line or part of it. splitToLines should be enabled
   */
  public static class MavenSpyEventsBuffer {
    private final StringBuilder myBuffer = new StringBuilder();
    private final BiConsumer<String, Key> myConsumer;

    public MavenSpyEventsBuffer(BiConsumer<String, Key> consumer) {myConsumer = consumer;}

    public void addText(@NotNull String text, @NotNull Key outputType) {
      if (text.charAt(text.length() - 1) == '\n') {
        String textToSend = myBuffer.length() == 0 ? text : myBuffer.toString() + text;
        myConsumer.accept(textToSend, outputType);
        myBuffer.setLength(0);
      }
      else {
        myBuffer.append(text);
      }
    }
  }

  public static class MavenSimpleConsoleEventsBuffer {
    private final StringBuilder myBuffer = new StringBuilder();
    private final BiConsumer<String, Key> myConsumer;
    private final boolean myShowSpyOutput;
    private boolean isProcessingSpyNow;

    public MavenSimpleConsoleEventsBuffer(BiConsumer<String, Key> consumer, boolean showSpyOutput) {
      myConsumer = consumer;
      myShowSpyOutput = showSpyOutput;
    }

    public void addText(@NotNull String text, @NotNull Key outputType) {
      if (myShowSpyOutput) {
        myConsumer.accept(text, outputType);
        return;
      }

      boolean lastChunk = text.charAt(text.length() - 1) == '\n';
      if (isProcessingSpyNow) {
        myBuffer.setLength(0);

        isProcessingSpyNow = !lastChunk;
        return;
      }


      String textToSend = myBuffer.length() == 0 ? text : myBuffer.toString() + text;
      if (textToSend.length() >= MavenSpyOutputParser.PREFIX.length() || lastChunk) {
        myBuffer.setLength(0);
        if (!MavenSpyOutputParser.isSpyLog(textToSend)) {
          myConsumer.accept(textToSend, outputType);
        }
        else {
          isProcessingSpyNow = !lastChunk;
        }
      }
      else {
        myBuffer.append(text);
      }
    }
  }
}
