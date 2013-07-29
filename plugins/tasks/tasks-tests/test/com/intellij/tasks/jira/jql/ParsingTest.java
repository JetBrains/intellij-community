package com.intellij.tasks.jira.jql;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;


/**
 * @author Mikhail Golubev
 */
public class ParsingTest extends ParsingTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.tasks.generic.ParsingTest");

  public ParsingTest() {
    super("psi", "jql", new JqlParserDefinition());
  }

  public void testIdentifiersParsing() throws Exception {
    doCodeTest("5b changed and 12 changed and foo\\ \\n\\t\\'\\\"\\\\bar changed and 'baz' changed and \"quux\" changed");
  }

  public void testOrOperators() throws Exception {
    doCodeTest("a = 42 or b > 42 || c < 42 | d != 42");
  }

  public void testAndOperators() throws Exception {
    doCodeTest("a = 42 and b > 42 && c < 42 & d != 42");
  }

  public void testNotOperators() throws Exception {
    doCodeTest("not a = 42 and ! b > 42");
  }

  public void testListParsing() throws Exception {
    doCodeTest("field in (1, \"2\", func1(), (3, func2()))");
  }

  public void testSimpleComparisons() throws Exception {
    doCodeTest("a1 < 42 or a2 <= 42 and " +
               "b1 > 42 or b2 >= 42 and " +
               "c1 ~ \"ham\" or c1 !~ 'spam' and " +
               "d1 = 'green' or d2 != \"green\"");
  }

  public void testIsClause() throws Exception {
    doCodeTest("a1 is empty or a2 is null or a3 is not empty or a4 is not null");
  }

  public void testSubclauses() throws Exception {
    doCodeTest("(a = foo or (b > 12 and ((c < 40)))) and d ~ 'foo'");
  }

  public void testWasClause() throws Exception {
    doCodeTest("status was in (open, 'closed', \"resolved\") during (\"-3d\", -1d) by Mark");
  }

  public void testChangedClause() throws Exception {
    doCodeTest("status changed from reported to resolved by Bob on '2012-11-15'");
  }

  public void testOrderByStatement() throws Exception {
    doCodeTest("assignee = John order by duedate desc, reported, votes asc");
  }

  public void testEmptyQuery() throws Exception {
    doCodeTest("");
  }

  @Override
  protected boolean includeRanges() {
    return true;
  }

  @Override
  protected boolean skipSpaces() {
    return false;
  }

  @Override
  protected String getTestDataPath() {
    // trailing slash will be inserted in superclass constructor
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/plugins/tasks/tasks-tests/testData/jira/jql";
  }
}
