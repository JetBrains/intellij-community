// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.completion;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ShKeywordCompletionTest extends LightCodeInsightFixtureTestCase {
  public void testIfCompletion() {
    myFixture.configureByText("a.sh", "if<caret>");
    myFixture.completeBasic();
    myFixture.type(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("if [  ]; then\n    <caret>\nfi");
  }

  public void testCompletionInsideIf() {
    myFixture.configureByText("a.sh", "if [ condition ]; then\n    <caret>\nfi");
    assertTrue(myFixture.completeBasic().length > 0);
  }

  public void testNoCompletionInIfCondition() {
    myFixture.configureByText("a.sh", "if [ if<caret> ]; then\n    \nfi");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "if [  ]; <caret>then\n    \nfi");
    assertEmpty(myFixture.completeBasic());
  }

  public void testElifCompletion() {
    myFixture.configureByText("a.sh", "if [  ]; then\n    \nelif<caret>\nfi");
    myFixture.completeBasic();
    myFixture.checkResult("if [  ]; then\n    \nelif [  ]; then\n    <caret>\nfi");
  }

  public void testCompletionInsideElif() {
    myFixture.configureByText("a.sh", "if [ condition ]; then\n    \nelif [ condition ]; then\n    <caret>\nfi");
    assertTrue(myFixture.completeBasic().length > 0);
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
    myFixture.checkResult("for (( i = 0; i < n; i++ )); do\n    <caret>\ndone");
  }

  public void testCompletionInsideFor() {
    myFixture.configureByText("a.sh", "for (( i = 0; i < 5; i++ )); do\n    <caret>\ndone");
    assertTrue(myFixture.completeBasic().length > 0);
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
    myFixture.checkResult("while [  ]; do\n    <caret>\ndone");
  }

  public void testCompletionInsideWhile() {
    myFixture.configureByText("a.sh", "while [ condition ]; do\n    <caret>\ndone");
    assertTrue(myFixture.completeBasic().length > 0);
  }

  public void testNoCompletionInWhileCondition() {
    myFixture.configureByText("a.sh", "while [ <caret> ]; do\n    \ndone");
    assertTrue(myFixture.completeBasic().length > 0);
    myFixture.configureByText("a.sh", "while [  ]whi<caret>; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "while [  ]; wh<caret> do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
  }

  public void testUntilCompletion() {
    myFixture.configureByText("a.sh", "until<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("until [  ]; do\n    <caret>\ndone");
  }

  public void testCompletionInsideUntil() {
    myFixture.configureByText("a.sh", "until [ condition ]; do\n    <caret>\ndone");
    assertTrue(myFixture.completeBasic().length > 0);
  }

  public void testNoCompletionInUntilCondition() {
    myFixture.configureByText("a.sh", "until [ <caret> ]; do\n    \ndone");
    assertTrue(myFixture.completeBasic().length > 0);
    myFixture.configureByText("a.sh", "until [  ]un<caret>; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "until [  ]; un<caret> do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
  }

  public void testSelectCompletion() {
    myFixture.configureByText("a.sh", "select<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("select item in *; do\n    <caret>\ndone");
  }

  public void testCompletionInsideSelect() {
    myFixture.configureByText("a.sh", "select  in ; do\n    <caret>\ndone");
    assertTrue(myFixture.completeBasic().length > 0);
  }

  public void testNoCompletionInSelectDeclaration() {
    myFixture.configureByText("a.sh", "select <caret> in ; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "select 3 4 in se<caret> ; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "select 3 sel<caret> ; do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "select 3  ; sel<caret>do\n    \ndone");
    assertEmpty(myFixture.completeBasic());
  }

  public void testCaseCompletion() {
    myFixture.configureByText("a.sh", "case<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("case $x in\npattern)\n  <caret>\n  ;;\nesac");
  }

  public void testCompletionInsideCase() {
    myFixture.configureByText("a.sh", "case $x in\npattern)\n  <caret>\n  ;;\nesac");
    assertTrue(myFixture.completeBasic().length > 0);
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

  public void testCompletionInsideFunction() {
    myFixture.configureByText("a.sh", "function foo() {\n    <caret>\n}");
    assertTrue(myFixture.completeBasic().length > 0);
  }

  public void testNoCompletionInFunctionDefinition() {
    myFixture.configureByText("a.sh", "function <caret>() {\n    \n}");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "function foo( f<caret> ) {\n    \n}");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "function foo() f<caret>{\n    \n}");
    assertEmpty(myFixture.completeBasic());
  }

  public void testStringEqual() {
    final String completionRule = "string equal";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $string1 == $string2 ]");
  }

  public void testStringNotEqual() {
    final String completionRule = "string not equal";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $string1 != $string2 ]");
  }

  public void testStringIsEmpty() {
    final String completionRule = "string is empty";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -z $string1 ]");
  }

  public void testStringNotEmpty() {
    final String completionRule = "string not empty";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -n $string1 ]");
  }

  public void testNumberEqual() {
    final String completionRule = "number equal";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $a -eq $b ]");
  }

  public void testNumberNotEqual() {
    final String completionRule = "number not equal";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $a -nq $b ]");
  }

  public void testNumberLess() {
    final String completionRule = "number less";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $a -lt $b ]");
  }

  public void testNumberLessOrEqual() {
    final String completionRule = "number less or equal";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $a -le $b ]");
  }

  public void testNumberGreater() {
    final String completionRule = "number greater";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $a -gt $b ]");
  }

  public void testNumberGreaterOrEqual() {
    final String completionRule = "number greater or equal";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $a -ge $b ]");
  }

  public void testFileExists() {
    final String completionRule = "file exists";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -f $file ]");
  }

  public void testFileNotEmpty() {
    final String completionRule = "file not empty";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -s $file ]");
  }

  public void testCommandExists() {
    final String completionRule = "command exists";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ `command -v command` ]");
  }

  public void testPathExists() {
    final String completionRule = "path exists";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -e $path ]");
  }

  public void testDirectoryExists() {
    final String completionRule = "directory exists";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -d $directory ]");
  }

  public void testFileReadable() {
    final String completionRule = "file readable";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -r $file ]");
  }

  public void testFileWritable() {
    final String completionRule = "file writable";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -w $file ]");
  }

  public void testFileExecutable() {
    final String completionRule = "file executable";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ -x $file ]");
  }

  public void testFileEquals() {
    final String completionRule = "file equals";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $file1 -ef $file2 ]");
  }

  public void testFileNewer() {
    final String completionRule = "file newer";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $file1 -nt $file2 ]");
  }

  public void testFileOlder() {
    final String completionRule = "file older";
    myFixture.configureByText("a.sh", "[ <caret> ]");
    completeByRule(completionRule);
    myFixture.checkResult("[ $file1 -ot $file2 ]");
  }

  public void testNoCompletionInArithmeticExpansions() {
    myFixture.configureByText("a.sh", "(( <caret> ))");
    assertEmpty(myFixture.completeBasic());
  }

  public void testNoCompletionInOldArithmeticExpansions() {
    myFixture.configureByText("a.sh", "$[ <caret> ]");
    assertEmpty(myFixture.completeBasic());
  }

  public void testNoCompletionInParameterExpansion() {
    myFixture.configureByText("a.sh", "${ <caret> }");
    assertEmpty(myFixture.completeBasic());
  }

  public void testNoKeywordCompletionInString() {
    myFixture.configureByText("a.sh", "\"<caret> \"");
    myFixture.completeBasic();
    assertLookupNotContainsTemplateKeywords();
  }

  public void testNoKeywordCompletionInRawString() {
    myFixture.configureByText("a.sh", "'<caret> '");
    myFixture.completeBasic();
    assertLookupNotContainsTemplateKeywords();
  }

  public void testNoCompletionInComment() {
    myFixture.configureByText("a.sh", "#<caret>");
    assertEmpty(myFixture.completeBasic());
  }

  public void testNoCompletionAfterDot() {
    myFixture.configureByText("a.sh", ".<caret>");
    assertEmpty(myFixture.completeBasic());
    myFixture.configureByText("a.sh", "#.<caret>");
    assertEmpty(myFixture.completeBasic());
  }

  public void testBashShebang() {
    final String completionRule = "#!/usr/bin/env bash";
    myFixture.configureByText("a.sh", "#!/usr<caret>");
    completeByRule(completionRule);
    myFixture.checkResult("#!/usr/bin/env bash");
  }

  public void testShShebang() {
    final String completionRule = "#!/usr/bin/env sh";
    myFixture.configureByText("a.sh", "#!/usr<caret>");
    completeByRule(completionRule);
    myFixture.checkResult("#!/usr/bin/env sh");
  }

  public void testZshShebang() {
    final String completionRule = "#!/usr/bin/env zsh";
    myFixture.configureByText("a.sh", "#!/usr<caret>");
    completeByRule(completionRule);
    myFixture.checkResult("#!/usr/bin/env zsh");
  }

  public void testNoShebang() {
    myFixture.configureByText("a.sh", "\n #!<caret>");
    assertEmpty(myFixture.completeBasic());
  }

  private void assertLookupNotContainsTemplateKeywords() {
    List<String> templateKeywords = ContainerUtil.newSmartList("if", "select", "case", "for", "while", "until", "function", "elif");
    LookupImpl lookup = (LookupImpl) myFixture.getLookup();
    assertNotNull(lookup);
    assertTrue(lookup.getItems().stream().noneMatch(item -> templateKeywords.contains(item.getLookupString())));
  }

  private void completeByRule(@NotNull String rule) {
    myFixture.completeBasic();
    LookupImpl lookup = (LookupImpl) myFixture.getLookup();
    assertNotNull(lookup);
    lookup.getItems().stream()
        .filter(item -> item.getLookupString().equals(rule))
        .findFirst().ifPresent(lookupElement -> {
      lookup.setCurrentItem(lookupElement);
      lookup.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    });
  }
}