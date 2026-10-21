// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.parser;

import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.yaml.YAMLParserDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;

@TestApplication
public class YAMLParserTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture = parsingFixture(
    projectFixture, PlatformTestUtil.getCommunityPath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/parser/data/", "yml",
    new YAMLParserDefinition());

  @Test
  public void test2docs() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 # Ranking of 1998 home runs
                 ---
                 - Mark McGwire
                 - Sammy Sosa
                 - Ken Griffey

                 # Team ranking
                 ---
                 - Chicago Cubs
                 - St Louis Cardinals""");
  }

  @Test
  public void testIndentation() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 name: Mark McGwire
                 accomplishment: >
                   Mark set a major league
                   home run record in 1998.
                 stats: |
                   65 Home Runs
                   0.278 Batting Average""");
  }

  @Test
  public void testMap_between_seq() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 ?
                   - Detroit Tigers
                   - Chicago cubs
                 :
                   - 2001-07-23

                 ? [ New York Yankees,
                     Atlanta Braves ]
                 : [ 2001-07-02, 2001-08-12,
                     2001-08-14 ]""");
  }

  @Test
  public void testFlowSequenceAsImplicitKey() throws Throwable {
    parsingFixture.get().doCodeTest("[a, b]: value");
  }

  @Test
  public void testFlowSequenceAsExplicitKeyInline() throws Throwable {
    parsingFixture.get().doCodeTest("? [a, b]: value");
  }

  @Test
  public void testFlowSequenceKeyMissingClosingBracket() throws Throwable {
    parsingFixture.get().doCodeTest("[a, b: value");
  }

  @Test
  public void testFlowSequenceKeyMissingColon() throws Throwable {
    parsingFixture.get().doCodeTest("[a, b] value");
  }

  @Test
  public void testFlowSequenceKeyMissingValue() throws Throwable {
    parsingFixture.get().doCodeTest("[a, b]:");
  }

  @Test
  public void testFlowSequenceKeyEmpty() throws Throwable {
    parsingFixture.get().doCodeTest("[]: value");
  }

  @Test
  public void testFlowSequenceKeyMissingOpeningBracket() throws Throwable {
    parsingFixture.get().doCodeTest("a, b]: value");
  }

  @Test
  public void testMap_map() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 Mark McGwire: {hr: 65, avg: 0.278}
                 Sammy Sosa: {
                     hr: 63,
                     avg: 0.288
                   }""");
  }

  @Test
  public void testSample_log() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 Stack:
                   - file: TopClass.py
                     line: 23
                     code: |
                       x = MoreObject("345\\n")
                   - file: MoreClass.py
                     line: 58
                     code: |-
                       foo = bar""");
  }

  @Test
  public void testSeq_seq() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 - [name        , hr, avg  ]
                 - [Mark McGwire, 65, 0.278]
                 - [Sammy Sosa  , 63, 0.288]""");
  }

  @Test
  public void testSequence_mappings() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 -
                   name: Mark McGwire
                   hr:   65
                   avg:  0.278
                 -
                   name: Sammy Sosa
                   hr:   63
                   avg:  0.288""");
  }

  @Test
  public void testBalance() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 runningTime: 150000
                 scenarios:
                     voice_bundle_change: {
                         dataCycling: true
                     }
                     smart_overview: {
                         dataCycling: true
                     }""");
  }

  @Test
  public void testInterpolation() throws Throwable {
    parsingFixture.get().doCodeTest("en:\n  foo: bar %{baz}");
  }

  @Test
  public void testValue_injection() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 key:
                     one: 1 text
                     other: some {{count}} text""");
  }

  @Test
  public void testSequence_idea76804() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 server:
                 - a
                 - b

                 server:
                   - a
                   - b""");
  }

  @Test
  public void testMultiline_ruby16796() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 code:
                   src="keys/{{item}}"
                   mode=0600
                 with_items:
                   - "id_rsa.pub"
                 """);
  }

  @Test
  public void testRuby17389() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 ---
                 foo: {}
                 bar: "baz\"""");
  }

  @Test
  public void testRuby19105() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 'Fn::Join':
                   - ''
                   - - Ref: hostedZoneName
                     - a""");
  }

  @Test
  public void testRuby15345() throws IOException {
    parsingFixture.get().doCodeTest("""
                 - !qualified.class.name
                     propertyOne: bla bla
                     propertyWithOneSequence:
                         - first value
                     nextPropertyWithOneSequence:
                         - first value of another sequence""");
  }

  @Test
  public void testHonestMultiline() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 ---
                 foo: >
                   first text line
                   second text line

                   baz: clazz
                   - this is text
                   - but looks like a list
                   - indent tells.
                 bar: zoo""");
  }

  @Test
  public void testEmptyMultiline() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 ---
                 foo: >
                 bar:
                   abc: def
                   ghi: >
                   jkl: mno
                 baz: qwe""");
  }

  @Test
  public void testIncompleteKey() throws Throwable {
    parsingFixture.get().doCodeTest("""
                 logging:
                   config: bla
                   index""");
  }

  @Test
  public void testStringWithTag() throws IOException {
    parsingFixture.get().doCodeTest("foo: ! \"tratata\"");
  }

  @Test
  public void testIncompleteKeyWithWhitespace() throws IOException {
    parsingFixture.get().doCodeTest("""
                 logging:
                   config:
                  \s
                  \s
                  \s
                  \s
                 """);
  }

  @Test
  public void testShiftedMap() throws IOException {
    parsingFixture.get().doCodeTest("    key: ttt\n" +
               "    ahahah: ppp");
  }

  @Test
  public void testShiftedList() throws IOException {
    parsingFixture.get().doCodeTest("    - item1\n" +
               "    - item2");
  }

  @Test
  public void testExplicitMaps() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testSpec2_27() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testAnsibleRoleElkInit() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testAnsibleRoleElkMain() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testBlockMapping() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testIncompleteKeyInHierarchy() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testKeyValueWithEmptyLineAhead() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testMultipleDocsWithMappings() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testScalarsWithNewlines() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testCommentInBlockScalarHeader() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testErrorInBlockScalarHeader() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testInlineMapWithBlockScalarValue()  {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testAliasInKey() { parsingFixture.get().doTest(true); }

  @Test
  public void testPlainMultilineScalarRuby21788() {
    parsingFixture.get().doTest(true);
  }

  @Test
  public void testAliasUseInArray() {
    parsingFixture.get().doTest(true);
  }
}
