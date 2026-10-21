package com.intellij.tasks.jira.jql;

import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;


/**
 * @author Mikhail Golubev
 */
@TestApplication
public class ParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, getTestDataPath() + "/psi", "jql", new JqlParserDefinition());

  @BeforeEach
  public void setUp() {
    parsingFixture.get().setIncludeRanges(true);
  }

  @Test
  public void testIdentifiersParsing() {
    parsingFixture.get().doCodeTest("5b changed and 12 changed and foo\\ \\n\\t\\'\\\"\\\\bar changed and 'baz' changed and \"quux\" changed");
  }

  @Test
  public void testOrOperators() {
    parsingFixture.get().doCodeTest("a = 42 or b > 42 || c < 42 | d != 42");
  }

  @Test
  public void testAndOperators() {
    parsingFixture.get().doCodeTest("a = 42 and b > 42 && c < 42 & d != 42");
  }

  @Test
  public void testNotOperators() {
    parsingFixture.get().doCodeTest("not a = 42 and ! b > 42");
  }

  @Test
  public void testListParsing() {
    parsingFixture.get().doCodeTest("field in (1, \"2\", func1(), (3, func2()))");
  }

  @Test
  public void testSimpleComparisons() {
    parsingFixture.get().doCodeTest("a1 < 42 or a2 <= 42 and " +
               "b1 > 42 or b2 >= 42 and " +
               "c1 ~ \"ham\" or c1 !~ 'spam' and " +
               "d1 = 'green' or d2 != \"green\"");
  }

  @Test
  public void testIsClause() {
    parsingFixture.get().doCodeTest("a1 is empty or a2 is null or a3 is not empty or a4 is not null");
  }

  @Test
  public void testSubclauses() {
    parsingFixture.get().doCodeTest("(a = foo or (b > 12 and ((c < 40)))) and d ~ 'foo'");
  }

  @Test
  public void testWasClause() {
    parsingFixture.get().doCodeTest("status was in (open, 'closed', \"resolved\") during (\"-3d\", -1d) by Mark");
  }

  @Test
  public void testChangedClause() {
    parsingFixture.get().doCodeTest("status changed from reported to resolved by Bob on '2012-11-15'");
  }

  @Test
  public void testOrderByStatement() {
    parsingFixture.get().doCodeTest("assignee = John order by duedate desc, reported, votes asc");
  }

  @Test
  public void testEmptyQuery() {
    parsingFixture.get().doCodeTest("");
  }

  private static String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/plugins/tasks/tests/base/testData/jira/jql";
  }
}
