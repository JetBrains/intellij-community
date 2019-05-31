// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.jira.jql;

import com.intellij.tasks.jira.jql.codeinsight.JqlFieldType;
import com.intellij.tasks.jira.jql.codeinsight.JqlStandardFunction;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

import static com.intellij.tasks.jira.jql.codeinsight.JqlStandardField.ALL_FIELD_NAMES;
import static com.intellij.tasks.jira.jql.codeinsight.JqlStandardFunction.ALL_FUNCTION_NAMES;

/**
 * @author Mikhail Golubev
 */
public class CompletionTest extends CodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return "/plugins/tasks/tasks-tests/testData/jira/jql/completion";
  }

  private String getTestFilePath() {
    return getTestName(false) + ".jql";
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  private void checkCompletionVariants(List<String> initial, String... others) {
    myFixture.testCompletionVariants(getTestFilePath(),
                                     ArrayUtilRt.toStringArray(ContainerUtil.append(initial, others)));
  }

  private void checkCompletionVariants(String... variants) {
    checkCompletionVariants(ContainerUtil.emptyList(), variants);
  }

  public void testBeginningOfLine() {
    checkCompletionVariants(ALL_FIELD_NAMES, "not");
  }

  public void testAfterClause() {
    checkCompletionVariants("and", "or", "order by");
  }

  public void testAfterFieldNameInClause() {
    checkCompletionVariants("was", "changed", "not", "is", "in");
  }

  public void testAfterFieldNameInSortKey() {
    checkCompletionVariants("asc", "desc");
  }

  public void testAfterIsKeyword() {
    checkCompletionVariants("empty", "null", "not");
  }

  public void testAfterIsNotKeywords() {
    checkCompletionVariants("empty", "null");
  }

  public void testAfterNotKeywordInTerminalClause() {
    checkCompletionVariants("in");
  }

  public void testAfterChangedKeyword() {
    checkCompletionVariants("and", "or", "order by",
                            "on", "by", "during", "after", "before", "to", "from");
  }

  public void testAfterWasClause() {
    checkCompletionVariants("and", "or", "order by",
                            "on", "by", "during", "after", "before", "to", "from");
  }

  public void testFunctionType1() {
    checkCompletionVariants("membersOf");
  }

  public void testFunctionType2() {
    checkCompletionVariants("currentUser");
  }

  public void testFunctionType3() {
    checkCompletionVariants("currentUser");
  }

  public void testFunctionType4() {
    checkCompletionVariants(JqlStandardFunction.allOfType(JqlFieldType.DATE, false));
  }

  public void testFunctionType5() {
    checkCompletionVariants(JqlStandardFunction.allOfType(JqlFieldType.DATE, false));
  }

  public void testAfterLeftParenthesisInSubClause() {
    checkCompletionVariants(ALL_FIELD_NAMES, "not");
  }

  public void testAfterSubClause() {
    checkCompletionVariants("and", "or", "order by");
  }

  public void testFunctionArguments() {
    // only literals accepted so we can't assume anything
    checkCompletionVariants(ContainerUtil.emptyList());
  }

  public void testAfterNotKeywordInNotClause() {
    checkCompletionVariants(ALL_FIELD_NAMES, "not");
  }

  public void testAfterOrderKeyword() {
    checkCompletionVariants("by");
  }

  public void testAfterWasKeyword() {
    checkCompletionVariants(ALL_FUNCTION_NAMES, "not", "in");
  }

  public void testInList() {
    checkCompletionVariants(ALL_FUNCTION_NAMES);
  }
}
