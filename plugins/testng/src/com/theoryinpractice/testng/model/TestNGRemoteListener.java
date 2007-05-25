/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 11, 2005
 * Time: 9:02:27 PM
 */
package com.theoryinpractice.testng.model;

import org.testng.remote.strprotocol.*;
import com.theoryinpractice.testng.ui.TestNGConsoleView;

public class TestNGRemoteListener implements IRemoteSuiteListener, IRemoteTestListener {
    private TestNGConsoleView console;

    public TestNGRemoteListener(TestNGConsoleView console) {
        this.console = console;
    }

    public void onInitialization(GenericMessage genericMessage) {
    }

    public void onStart(SuiteMessage suiteMessage) {
        console.getResultsView().start();
    }

    public void onFinish(SuiteMessage suiteMessage) {
        console.getResultsView().finish();
    }

    public void onStart(TestMessage tm) {
        console.getResultsView().setTotal(tm.getTestMethodCount());
    }

    public void onTestStart(TestResultMessage trm) {
        console.addTestResult(trm);
    }

    public void onFinish(TestMessage tm) {
        console.rebuildTree();
    }

    public void onTestSuccess(TestResultMessage trm) {
        console.addTestResult(trm);
    }

    public void onTestFailure(TestResultMessage trm) {
        console.addTestResult(trm);
    }

    public void onTestSkipped(TestResultMessage trm) {
        console.addTestResult(trm);
    }

    public void onTestFailedButWithinSuccessPercentage(TestResultMessage trm) {
    }
}