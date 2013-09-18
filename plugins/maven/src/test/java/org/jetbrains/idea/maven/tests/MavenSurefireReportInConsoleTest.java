package org.jetbrains.idea.maven.tests;

import com.intellij.execution.filters.Filter;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import junit.framework.Assert;
import org.jetbrains.idea.maven.project.MavenTestConsoleFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenSurefireReportInConsoleTest extends LightCodeInsightFixtureTestCase {

  private Filter myFilter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFilter = new MavenTestConsoleFilter(getProject());
  }

  private List<String> passLine(String line) {
    if (!line.endsWith("\n")) {
      line += '\n';
    }

    Filter.Result result = myFilter.applyFilter(line, line.length());
    if (result == null) return Collections.emptyList();

    List<String> res = new ArrayList<String>();

    for (Filter.ResultItem item : result.getResultItems()) {
      res.add(line.substring(item.highlightStartOffset, item.highlightEndOffset));
    }

    return res;
  }

  public void testSurefire2_14() {
    myFixture.addClass("public class CccTest {\n" +
                       "  public void testTtt() {}\n" +
                       "  public void testTtt2() {}\n" +
                       "}");

    String tempDirPath = myFixture.getTempDirPath();

    Assert.assertEquals(passLine("[INFO] Scanning for projects..."), Collections.emptyList());
    Assert.assertEquals(passLine("[INFO] Surefire report directory: " + tempDirPath), Collections.singletonList(tempDirPath));
    Assert.assertEquals(passLine("[ERROR] Please refer to " + tempDirPath + " for the individual test results."), Collections.singletonList(
      tempDirPath));
  }

}
