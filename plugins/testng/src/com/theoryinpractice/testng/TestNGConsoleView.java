/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 9, 2005
 * Time: 4:19:20 PM
 */
package com.theoryinpractice.testng;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuidlerFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.theoryinpractice.testng.model.TestNGConsoleProperties;
import com.theoryinpractice.testng.ui.TestNGResults;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

public class TestNGConsoleView implements ConsoleView
{
    private ConsoleView console;
    private TestNGResults testNGResults;
    private final List<Chunk> allOutput = new ArrayList<Chunk>();
    private int mark;
    private TestNGConsoleProperties consoleProperties;

    public TestNGConsoleView(Project project, TestNGConsoleProperties consoleProperties) {
        this.consoleProperties = consoleProperties;
        buildView(project);
    }

    public TestNGResults getResultsView() {
        return testNGResults;
    }

    public JComponent getComponent() {
        return testNGResults.getMain();
    }

    public TestNGConsoleProperties getConsoleProperties() {
        return consoleProperties;
    }

    public void addTestResult(TestResultMessage result) {
        List<Chunk> list = null;
        if (result.getResult() == MessageHelper.TEST_STARTED) {
            mark();
        } else {
            if (result.getStackTrace() != null && result.getStackTrace().length() > 10) {
                //trim useless crud from stacktrace
                String[] lines = result.getStackTrace().split("\n");
                StringBuilder builder = new StringBuilder();

                if (lines.length > 0) {
                    int i = lines.length - 1;
                    while (i >= 0) {
                        //first 4 chars are '\t at '
                        if (lines[i].length() > 4 &&  (lines[i].startsWith("org.testng.", 4)
                                || lines[i].startsWith("sun.reflect.DelegatingMethodAccessorImpl", 4)
                                || lines[i].startsWith("sun.reflect.NativeMethodAccessorImpl", 4)
                                || lines[i].startsWith("java.lang.reflect.Method", 4)
                                )) {

                        } else {
                            //we're done with internals, so we know the rest are ok
                            break;
                        }
                        i--;
                    }
                    for (int j = 0; j <= i; j++) {
                        builder.append(lines[j]);
                        builder.append('\n');
                    }
                }

                print(builder.toString(), ConsoleViewContentType.ERROR_OUTPUT);
            }
            list = getChunksSinceMark();
        }

        testNGResults.addTestResult(result, list);
    }

    private void buildView(Project project) {
        console = TextConsoleBuidlerFactory.getInstance().createBuilder(project).getConsole();
        testNGResults = new TestNGResults(project, this);
        testNGResults.getTabbedPane().add("Output", console.getComponent());
        testNGResults.getTabbedPane().add("Statistics", new JScrollPane(testNGResults.getResultsTable()));
    }

    public void mark() {
        mark = allOutput.size();
    }

    public List<Chunk> getChunksSinceMark() {
        synchronized (allOutput) {
            return new ArrayList<Chunk>(allOutput.subList(mark, allOutput.size()));
        }
    }

    public void dispose() {
        console.dispose();
    }

    public void print(String s, ConsoleViewContentType contentType) {
        synchronized (allOutput) {
            allOutput.add(new Chunk(s, contentType));
        }
    }

    public void reset() {
        setView(allOutput);
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

    public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
        console.printHyperlink(hyperlinkText, info);
    }

    public int getContentSize() {
        return console.getContentSize();
    }

    public boolean canPause() {
        return console.canPause();
    }

    public void setView(final List<Chunk> output) {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run() {
                    setView(output);
                }
            });
        } else {
            console.clear();
            for (Chunk chunk : output) {
                console.print(chunk.text, chunk.contentType);
            }
        }
    }

    public static class Chunk
    {
        public String text;
        public ConsoleViewContentType contentType;

        public Chunk(String text, ConsoleViewContentType contentType) {
            this.text = text;
            this.contentType = contentType;
        }
    }
}