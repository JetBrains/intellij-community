package com.intellij.bash.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class BashKeywordCompletionTest extends LightCodeInsightFixtureTestCase {

  public void testIfCompletion() {
    myFixture.configureByText("a.sh", "if<caret>");
    myFixture.completeBasic();
    myFixture.type(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("if [ condition ]; then\n    <caret>\nfi");
  }

  public void testNoCompletionInIfCondition() {
    myFixture.configureByText("a.sh", "if [ if<caret> ]; then\n    \nfi");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "if [  ]; <caret>then\n    \nfi");
    assertEmpty(myFixture.completeBasic());
  }

  public void testElifCompletion() {
    myFixture.configureByText("a.sh", "if [ condition ]; then\n    \nelif<caret>\nfi");
    myFixture.completeBasic();
    myFixture.checkResult("if [ condition ]; then\n    \nelif [ condition ]; then\n    <caret>\nfi");
  }

  public void testNoCompletionInElifCondition() {
    myFixture.configureByText("a.sh", "if [ condition ]; then\n    \nelif [ if<caret> ]; then\n    \nfi");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "if [ condition ]; then\n    \nelif [  ]; <caret>then\n    \nfi");
    assertEmpty(myFixture.completeBasic());
  }

  public void testNoEilfCompletion() {
    myFixture.configureByText("a.sh", "for (( i = 0; i < ; i++ )); do\n    elif<caret>\ndone");
    assertEmpty(myFixture.completeBasic());
  }

  public void testForCompletion() {
    myFixture.configureByText("a.sh", "for<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("for (( i = 0; i < ; i++ )); do\n    <caret>\ndone");
  }

  public void testNoCompletionInForCondition() {
    myFixture.configureByText("a.sh", "for (( i = 0; i < <caret>; i++ )); do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "for (( i = 0; i < 5; i++ fo<caret> )); do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "for (( i = 0; i < 5; i++ )) fo<caret> ; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "for (( i = 0; i < 5; i++ )); fo<caret> do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
  }

  public void testWhileCompletion() {
    myFixture.configureByText("a.sh", "while<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("while [ condition ]; do\n    <caret>\ndone");
  }

  public void testNoCompletionInWhileCondition() {
    myFixture.configureByText("a.sh", "while [ <caret> ]; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "while [  ]whi<caret>; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "while [  ]; wh<caret> do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
  }

  public void testUntilCompletion() {
    myFixture.configureByText("a.sh", "until<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("until [ condition ]; do\n    <caret>\ndone");
  }

  public void testNoCompletionInUntilCondition() {
    myFixture.configureByText("a.sh", "until [ <caret> ]; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "until [  ]un<caret>; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "until [  ]; un<caret> do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
  }

  public void testSelectCompletion() {
    myFixture.configureByText("a.sh", "select<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("select  in ; do\n    <caret>\ndone");
  }

  public void testNoCompletionInSelectDeclaration() {
    myFixture.configureByText("a.sh", "select <caret> in ; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
  }

  public void testCaseCompletion() {
    myFixture.configureByText("a.sh", "case<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("case $x in\npattern)\n  <caret>\n  ;;\nesac");
  }

  public void testNoCompletionInCaseDeclaration() {
    myFixture.configureByText("a.sh", "case <caret> in\npattern)\n  \n  ;;\nesac");
    assertEmpty(myFixture.completeBasic());
  }

  public void testFunctionCompletion() {
    myFixture.configureByText("a.sh", "function<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("function foo() {\n    <caret>\n}");
  }

  public void testNoCompletionInFunctionDefinition() {
    myFixture.configureByText("a.sh", "function <caret>() {\n    \n}");
    assertEmpty(myFixture.completeBasic());
  }
}