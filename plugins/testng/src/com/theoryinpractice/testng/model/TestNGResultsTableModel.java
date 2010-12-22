/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: amrk
 * Date: Jul 9, 2005
 * Time: 3:37:27 PM
 */
package com.theoryinpractice.testng.model;

import com.intellij.openapi.project.Project;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.testng.remote.strprotocol.MessageHelper;
import org.testng.remote.strprotocol.TestResultMessage;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TestNGResultsTableModel extends ListTableModel<TestResultMessage> {

  private final List<TestResultMessage> testResults;

  public TestNGResultsTableModel(Project project) {
    super(new StatusColumnInfo(), new TestNameColumnInfo(project), new TestClassNameColumnInfo(project), new DurationColumnInfo());
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
    private final Project project;

    public TestNameColumnInfo(Project project) {
      super("Test");
      this.project = project;
    }

    public String valueOf(final TestResultMessage result) {
      final String displayString = TestProxy.toDisplayText(result, project);
      final String description = result.getTestDescription();
      if (description != null && description.startsWith(displayString)) return description;
      return displayString;
    }

    public Comparator<TestResultMessage> getComparator() {
      return new Comparator<TestResultMessage>() {
        public int compare(final TestResultMessage o1, final TestResultMessage o2) {
          return o1.getMethod().compareToIgnoreCase(o2.getMethod());
        }
      };
    }
  }

  private static class TestClassNameColumnInfo extends ColumnInfo<TestResultMessage, String> {
    private final Project project;

    public TestClassNameColumnInfo(Project project) {
      super("Test Class");
      this.project = project;
    }

    public String valueOf(final TestResultMessage result) {
      final String description = result.getTestClass();
      if (description != null) return description;
      return TestProxy.toDisplayText(result, project);
    }

    public Comparator<TestResultMessage> getComparator() {
      return new Comparator<TestResultMessage>() {
        public int compare(final TestResultMessage o1, final TestResultMessage o2) {
          return o1.getTestClass().compareToIgnoreCase(o2.getTestClass());
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
