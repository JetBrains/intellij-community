package org.jetbrains.plugins.ruby.testing.testunit.runner.ui;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.testing.testunit.runConfigurations.RTestsRunConfiguration;
import org.jetbrains.plugins.ruby.testing.testunit.runner.properties.RTestUnitConsoleProperties;
import org.jetbrains.plugins.ruby.RBundle;

import javax.swing.*;

/**
 * @author: Roman Chernyatchik
 */
public class RTestUnitConsoleView implements ConsoleView {
  private RTestUnitConsoleProperties myConsoleProperties;

  private RTestUnitResultsForm myResultsForm;

  private ConsoleView myConsole;

  public RTestUnitConsoleView(final RTestsRunConfiguration runConfig) {
    final Project project = runConfig.getProject();

    // Console
    myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    myConsoleProperties = new RTestUnitConsoleProperties(runConfig);
    myConsoleProperties.setConsole(this);

    // Results View
    myResultsForm = new RTestUnitResultsForm(runConfig, myConsoleProperties);
    myResultsForm.addTab(RBundle.message("ruby.test.runner.ui.tabs.output.title"), myConsole.getComponent());
    //TODO[romeo] add tabs: statistics
    myResultsForm.initLogConsole();
  }

  @NotNull
  public RTestUnitResultsForm getResultsForm() {
    return myResultsForm;
  }

  public RTestUnitConsoleProperties getConsoleProperties() {
      return myConsoleProperties;
  }

  public void print(final String s, final ConsoleViewContentType contentType) {
    //TODO[romeo] implement
  }

  public void clear() {
    myConsole.clear();
  }

  public void scrollTo(final int offset) {
    myConsole.scrollTo(offset);
  }

  public void attachToProcess(final ProcessHandler processHandler) {
    myConsole.attachToProcess(processHandler);
    myResultsForm.attachStopLogConsoleTrackingListeners(processHandler);
  }

  public void setOutputPaused(final boolean value) {
    myConsole.setOutputPaused(value);
  }

  public boolean isOutputPaused() {
    return myConsole != null && myConsole.isOutputPaused();
  }

  public boolean hasDeferredOutput() {
    return myConsole.hasDeferredOutput();
  }

  public void performWhenNoDeferredOutput(final Runnable runnable) {
    myConsole.performWhenNoDeferredOutput(runnable);
  }

  public void setHelpId(final String helpId) {
    myConsole.setHelpId(helpId);
  }

  public void addMessageFilter(final Filter filter) {
    myConsole.addMessageFilter(filter);
  }

  public void printHyperlink(final String hyperlinkText, final HyperlinkInfo info) {
    myConsole.printHyperlink(hyperlinkText, info);
  }

  public int getContentSize() {
    return myConsole.getContentSize();
  }

  public boolean canPause() {
    return myConsole != null && myConsole.canPause();
  }

  @NotNull
  public AnAction[] createUpDownStacktraceActions() {
    return myConsole.createUpDownStacktraceActions();
  }

  public JComponent getComponent() {
    return myResultsForm.getContentPane();
  }

  public JComponent getPreferredFocusableComponent() {
    return myConsole.getComponent();
  }

  public void dispose() {
    Disposer.dispose(myConsole);
    myConsole = null;

    Disposer.dispose(myResultsForm);
    myResultsForm = null;

    myConsoleProperties.dispose();
    myConsoleProperties = null;
  }
}
