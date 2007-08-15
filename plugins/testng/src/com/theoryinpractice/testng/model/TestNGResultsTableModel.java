/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 9, 2005
 * Time: 3:37:27 PM
 */
package com.theoryinpractice.testng.model;

import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TestNGResultsTableModel extends ListTableModel<TestResultMessage> {

  private List<TestResultMessage> testResults;

  public TestNGResultsTableModel() {
    super(new StatusColumnInfo(), new TestNameColumnInfo(), new DurationColumnInfo());
    testResults = new ArrayList<TestResultMessage>();
  }

  public void addTestResult(TestResultMessage result) {
    testResults.add(result);
    setItems(testResults);
  }

  private static long getDuration(final TestResultMessage result) {
    return result.getEndMillis() - result.getStartMillis();
  }

  private static class StatusColumnInfo extends ColumnInfo<TestResultMessage, String> {
    public StatusColumnInfo() {
      super("Status");
    }

    public String valueOf(final TestResultMessage result) {
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

    public Comparator<TestResultMessage> getComparator() {
      return new Comparator<TestResultMessage>() {
        public int compare(final TestResultMessage o1, final TestResultMessage o2) {
          return o1.getResult() - o2.getResult();
        }
      };
    }
  }

  private static class TestNameColumnInfo extends ColumnInfo<TestResultMessage, String> {
    public TestNameColumnInfo() {
      super("Test");
    }

    public String valueOf(final TestResultMessage result) {
      return result.toDisplayString();
    }

    public Comparator<TestResultMessage> getComparator() {
      return new Comparator<TestResultMessage>() {
        public int compare(final TestResultMessage o1, final TestResultMessage o2) {
          return o1.getMethod().compareToIgnoreCase(o2.getMethod());
        }
      };
    }
  }

  private static class DurationColumnInfo extends ColumnInfo<TestResultMessage, String> {
    public DurationColumnInfo() {
      super("Time");
    }

    public String valueOf(final TestResultMessage result) {
      long time = getDuration(result);
      if (time == 0) {
        return "0.0 s";
      }
      return NumberFormat.getInstance().format((double)time/1000.0) + " s";
    }

    public Comparator<TestResultMessage> getComparator() {
      return new Comparator<TestResultMessage>() {
        public int compare(final TestResultMessage o1, final TestResultMessage o2) {
          return (int)(getDuration(o1) - getDuration(o2));
        }
      };
    }
  }
}