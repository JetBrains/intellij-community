package com.intellij.execution.testframework.sm;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.statistics.StatisticsPanel;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerUIActionsHandler;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;

/**
 * @author Roman Chernyatchik
 */
public class SMTestRunnerConnectionUtil {
  private SMTestRunnerConnectionUtil() {
    // Do nothing. Utility class.
  }

  /**
   * Creates Test Runner console component with test tree, console, statistics tabs
   * and attaches it to given Process handler.
   *
   * You can use this method in run configuration's CommandLineState. You should
   * just override "execute" method of your custom command line state and return
   * test runner's console.
   *
   * @param project Project
   * @param processHandler Process handler
   * @param consoleProperties Console properties for test console actions
   * @param resultsViewer Customized tests runner console container
   * @return Console view
   * @throws ExecutionException If IDEA cannot execute process this Exception will
   * be catched and shown in error message box
   */
  public static BaseTestsOutputConsoleView attachRunner(@NotNull final Project project,
                                                        @NotNull final ProcessHandler processHandler,
                                                        final TestConsoleProperties consoleProperties,
                                                        final SMTestRunnerResultsForm resultsViewer) throws ExecutionException {

    // Console
    final SMTRunnerConsoleView testRunnerConsole = new SMTRunnerConsoleView(consoleProperties, resultsViewer);

    // Statistics tab
    final StatisticsPanel statisticsPane = new StatisticsPanel(project, resultsViewer);
    resultsViewer.addTab(ExecutionBundle.message("statistics.tab.title"), null,
                         StatisticsPanel.STATISTICS_TAB_ICON,
                         statisticsPane.getContentPane());
    // handler to select in results viewer by statistics pane events
    statisticsPane.addPropagateSelectionListener(resultsViewer.createSelectMeListener());
    // handler to select test statistics pane by result viewer events
    resultsViewer.setShowStatisticForProxyHandler(statisticsPane.createSelectMeListener());

    // attach listeners
    attachEventsProcessors(consoleProperties, resultsViewer, statisticsPane, processHandler);
    testRunnerConsole.attachToProcess(processHandler);

    return testRunnerConsole;
  }

  /**
   * Creates Test Runner console component with test tree, console, statistics tabs
   * and attaches it to given Process handler.
   *
   * You can use this method in run configuration's CommandLineState. You should
   * just override "execute" method of your custom command line state and return
   * test runner's console.
   *
   * E.g:
   * <code>
   * public class MyCommandLineState extends CommandLineState {
   *
   *   // ...
   *
   *   @Override
   *   public ExecutionResult execute(@NotNull final Executor executor,
   *                                  @NotNull final ProgramRunner runner) throws ExecutionException {
   *
   *     final ProcessHandler processHandler = startProcess();
   *     final AbstractRubyRunConfiguration runConfiguration = getConfig();
   *     final Project project = runConfiguration.getProject();
   *
   *     final ConsoleView console =
   *       SMTestRunnerConnectionUtil.attachRunner(project, processHandler, this, runConfiguration,
   *                                               "MY_TESTRUNNER_SPLITTER_SETTINGS");
   *
   *     return new DefaultExecutionResult(console, processHandler,
   *                                      createActions(console, processHandler));
   *    }
   * }
   * </code>
   *
   * @param project Project
   * @param processHandler Process handler
   * @param commandLineState  Command line state
   * @param config User run configuration settings
   * @param splitterPropertyName This property will be used for storing splitter position.
   * @return Console view
   * @throws ExecutionException If IDEA cannot execute process this Exception will
   * be catched and shown in error message box
   */
  public static ConsoleView attachRunner(@NotNull final Project project,
                                         @NotNull final ProcessHandler processHandler,
                                         @NotNull final CommandLineState commandLineState,
                                         @NotNull final RuntimeConfiguration config,
                                         @Nullable final String splitterPropertyName) throws ExecutionException {
    final TestConsoleProperties consoleProperties = new SMTRunnerConsoleProperties(config);

    // Results viewer component
    final SMTestRunnerResultsForm resultsViewer =
      new SMTestRunnerResultsForm(config, consoleProperties,
                                  commandLineState.getRunnerSettings(),
                                  commandLineState.getConfigurationSettings(),
                                  splitterPropertyName);

    return attachRunner(project, processHandler, consoleProperties, resultsViewer);
  }

  private static ProcessHandler attachEventsProcessors(final TestConsoleProperties consoleProperties,
                                                       final SMTestRunnerResultsForm resultsViewer,
                                                       final StatisticsPanel statisticsPane,
                                                       final ProcessHandler processHandler)
      throws ExecutionException {

    //build messages consumer
    final OutputToGeneralTestEventsConverter outputConsumer = new OutputToGeneralTestEventsConverter();

    //events processor
    final GeneralToSMTRunnerEventsConvertor eventsProcessor = new GeneralToSMTRunnerEventsConvertor(resultsViewer.getTestsRootNode());

    //ui actions
    final SMTRunnerUIActionsHandler uiActionsHandler = new SMTRunnerUIActionsHandler(consoleProperties);

    // subscribe on events

    // subsrcibes event processor on output consumer events
    outputConsumer.addProcessor(eventsProcessor);
    // subsrcibes result viewer on event processor
    eventsProcessor.addEventsListener(resultsViewer);
    // subsrcibes test runner's actions on results viewer events
    resultsViewer.addEventsListener(uiActionsHandler);
    // subsrcibes statistics tab viewer on event processor
    eventsProcessor.addEventsListener(statisticsPane.createTestEventsListener());

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        Disposer.dispose(eventsProcessor);
        Disposer.dispose(outputConsumer);
      }

      @Override
      public void startNotified(final ProcessEvent event) {
        eventsProcessor.onStartTesting();
      }

      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        outputConsumer.flushBufferBeforeTerminating();
        eventsProcessor.onFinishTesting();
      }

      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        outputConsumer.process(event.getText(), outputType);
      }
    });
    return processHandler;
  }

}
