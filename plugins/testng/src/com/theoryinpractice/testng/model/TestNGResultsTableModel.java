/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 9, 2005
 * Time: 3:37:27 PM
 */
package com.theoryinpractice.testng.model;

import java.util.ArrayList;
import java.util.List;

import com.intellij.execution.junit2.ui.Formatters;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

public class TestNGResultsTableModel extends ListTableModel<TestResultMessage>
{

    private List<TestResultMessage> testResults;

    public TestNGResultsTableModel() {
        super(defaultColumns());
        this.testResults = new ArrayList<TestResultMessage>();
    }

    private static ColumnInfo[] defaultColumns() {
        return new ColumnInfo[] {
                new ColumnInfo("Status")
                {
                    @Override
                    public Object valueOf(Object object) {
                        TestResultMessage result = (TestResultMessage) object;
                        long time = result.getEndMillis() - result.getStartMillis();
                        System.out.println("time is " + time);
                        switch (result.getResult()) {
                            case MessageHelper.PASSED_TEST:
                                return "Pass";
                            case MessageHelper.FAILED_TEST:
                                return "Fail";
                            case MessageHelper.SKIPPED_TEST:
                                return "Skipped";
                            case MessageHelper.FAILED_ON_PERCENTAGE_TEST:
                                return "Failed On %";
                            default:
                                return "Unknown result " + result.getResult();
                        }
                    }
                },
                new ColumnInfo("Test")
                {
                    @Override
                    public Object valueOf(Object object) {
                        TestResultMessage result = (TestResultMessage) object;
                        return result.getMethod();
                    }
                },
                new ColumnInfo("Time")
                {
                    @Override
                    public Object valueOf(Object object) {
                        TestResultMessage result = (TestResultMessage) object;
                        long time = result.getEndMillis() - result.getStartMillis();
                        System.out.println("time is " + time);
                        return Formatters.printTime(time);
                    }
                }};
    }

    public void addTestResult(TestResultMessage result) {
        testResults.add(result);
        setItems(testResults);
    }

    public List<TestResultMessage> getTestResults() {
        return testResults;
    }
}