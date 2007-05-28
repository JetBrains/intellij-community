/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 9, 2005
 * Time: 4:19:20 PM
 */
package com.theoryinpractice.testng.ui;

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import org.jetbrains.annotations.NotNull;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TestNGConsoleView implements ConsoleView
{
    private ConsoleView console;
    private TestNGResults testNGResults;
    private final List<Printable> allOutput = new ArrayList<Printable>();
    private int mark;
    private TestNGConsoleProperties consoleProperties;

    public TestNGConsoleView(Project project, TestNGConsoleProperties consoleProperties, final RunnerSettings runnerSettings,
                             final ConfigurationPerRunnerSettings configurationPerRunnerSettings) {
      this.consoleProperties = consoleProperties;
      console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
      consoleProperties.setConsole(this);
      testNGResults = new TestNGResults(project, this, runnerSettings, configurationPerRunnerSettings);
      testNGResults.getTabbedPane().add("Output", console.getComponent());
      testNGResults.getTabbedPane().add("Statistics", new JScrollPane(testNGResults.getResultsTable()));
    }

    public TestNGResults getResultsView() {
        return testNGResults;
    }

    public JComponent getComponent() {
        return testNGResults.getMain();
    }

    public void rebuildTree() {
        testNGResults.rebuildTree();
    }

    public TestNGConsoleProperties getConsoleProperties() {
        return consoleProperties;
    }

    public void addTestResult(TestResultMessage result) {
        if (testNGResults != null) {
            List<Printable> list = null;
            int exceptionMark = 0;
            if (result.getResult() == MessageHelper.TEST_STARTED) {
                mark();
            } else {
                String stackTrace = result.getStackTrace();
                if (stackTrace != null && stackTrace.length() > 10) {
                    //trim useless crud from stacktrace
                  exceptionMark = allOutput.size() - mark;
                  String trimmed = trimStackTrace(stackTrace);
                  List<Printable> printables = getPrintables(result, trimmed, ConsoleViewContentType.ERROR_OUTPUT);
                  synchronized (allOutput) {
                        allOutput.addAll(printables);
                    }
                }
                list = getPrintablesSinceMark();
            }

            testNGResults.addTestResult(result, list, exceptionMark);
        }
    }

    private String trimStackTrace(String stackTrace) {
        String[] lines = stackTrace.split("\n");
        StringBuilder builder = new StringBuilder();

        if (lines.length > 0) {
            int i = lines.length - 1;
            while (i >= 0) {
                //first 4 chars are '\t at '
                int startIndex = lines[i].indexOf('a') + 3;
                if (lines[i].length() > 4 && (lines[i].startsWith("org.testng.", startIndex)
                        || lines[i].startsWith("sun.reflect.DelegatingMethodAccessorImpl", startIndex)
                        || lines[i].startsWith("sun.reflect.NativeMethodAccessorImpl", startIndex)
                        || lines[i].startsWith("java.lang.reflect.Method", startIndex)
                        || lines[i].startsWith("com.intellij.rt.execution.application.AppMain", startIndex)
                )) {

                } else {
                    // we're done with internals, so we know the rest are ok
                    break;
                }
                i--;
            }
            for (int j = 0; j <= i; j++) {
                builder.append(lines[j]);
                builder.append('\n');
            }
        }
        return builder.toString();
    }

  public void mark() {
        mark = allOutput.size();
    }

    public List<Printable> getPrintablesSinceMark() {
        synchronized (allOutput) {
            return new ArrayList<Printable>(allOutput.subList(mark, allOutput.size()));
        }
    }

    public void dispose() {
        console.dispose();
        console = null;
        testNGResults.dispose();
        testNGResults = null;
        consoleProperties.dispose();
        consoleProperties = null;
    }

    private List<Printable> getPrintables(final TestResultMessage result, String s, ConsoleViewContentType type) {
        List<Printable> printables = new ArrayList<Printable>();
        //figure out if we have a diff we need to hyperlink
        //TODO replace this with a saner regexp
        String assertText = "java.lang.AssertionError: expected:<";
        if (s.startsWith(assertText)) {
            printables.add(new Chunk("java.lang.AssertionError:", type));
            String end = "> but was:<";
            int actualStart = s.indexOf(end, assertText.length());
            String expected = s.substring(assertText.length(), actualStart);
            int actualEnd = s.indexOf("org.testng.Assert", actualStart + end.length());
            actualEnd = s.lastIndexOf('>', actualEnd);
            int stackTraceEnd = s.lastIndexOf("org.testng.Assert.");
            stackTraceEnd = s.indexOf('\n', stackTraceEnd) + 1;
            //we have an assert with expected/actual, so we parse it out and create a diff hyperlink
            TestNGDiffHyperLink link = new TestNGDiffHyperLink(expected, s.substring(actualStart + end.length(), actualEnd), null, consoleProperties) {
              protected String getTitle() {
                //TODO should do some more farting about to find the equality assertion that failed and show that as title
                return result.getTestClass() + '#' + result.getMethod() + "() failed";
              }
            };
            //same as junit diff view
            printables.add(link);
            printables.add(new Chunk(trimStackTrace(s.substring(stackTraceEnd)), type));
        } else {
            printables.add(new Chunk(s, type));
        }
        return printables;
    }

    public void print(String s, ConsoleViewContentType contentType) {
        Chunk chunk = new Chunk(s, contentType);
        synchronized (allOutput) {
            allOutput.add(chunk);
        }
    }

    public void reset() {
        setView(allOutput, 0);
    }

    public void clear() {
        console.clear();
    }

    public void scrollTo(int offset) {
        console.scrollTo(offset);
    }

    public void attachToProcess(ProcessHandler processHandler) {
        console.attachToProcess(processHandler);
    }

    public void setOutputPaused(boolean value) {
        console.setOutputPaused(value);
    }

    public boolean isOutputPaused() {
        return console.isOutputPaused();
    }

    public boolean hasDeferredOutput() {
        return console.hasDeferredOutput();
    }

    public void performWhenNoDeferredOutput(Runnable runnable) {
        console.performWhenNoDeferredOutput(runnable);
    }

    public void setHelpId(String helpId) {
        console.setHelpId(helpId);
    }

    public void addMessageFilter(Filter filter) {
        console.addMessageFilter(filter);
    }

    public JComponent getPreferredFocusableComponent() {
        return console.getComponent();
    }

    public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
        console.printHyperlink(hyperlinkText, info);
    }

    public int getContentSize() {
        return console.getContentSize();
    }

    public boolean canPause() {
        return console.canPause();
    }

  @NotNull
  public AnAction[] createUpDownStacktraceActions() {
    return console.createUpDownStacktraceActions();
  }

  public void setView(final List<Printable> output, final int i) {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run() {
                    setView(output, i);
                }
            });
        } else {
            console.clear();
            int idx = 0;
            int offset = 0;
            for (Printable chunk : new ArrayList<Printable>(output)) {
              chunk.print(console);
              if (idx++ < i) {
                offset = console.getContentSize();
              }
            }
            console.scrollTo(offset);
        }
    }

    public static class Chunk implements Printable
    {
        public String text;
        public ConsoleViewContentType contentType;

        public void print(ConsoleView console) {
            console.print(text, contentType);
        }

        public Chunk(String text, ConsoleViewContentType contentType) {
            this.text = text;
            this.contentType = contentType;
        }
    }
}