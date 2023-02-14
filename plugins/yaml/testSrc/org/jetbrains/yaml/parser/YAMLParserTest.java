// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.parser;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.yaml.YAMLParserDefinition;

import java.io.IOException;

public class YAMLParserTest extends ParsingTestCase {

  public YAMLParserTest() {
    super("", "yml", new YAMLParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/parser/data/";
  }

  public void test2docs() throws Throwable {
    doCodeTest("""
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

  public void testIndentation() throws Throwable {
    doCodeTest("""
                 name: Mark McGwire
                 accomplishment: >
                   Mark set a major league
                   home run record in 1998.
                 stats: |
                   65 Home Runs
                   0.278 Batting Average""");
  }

  public void testMap_between_seq() throws Throwable {
    doCodeTest("""
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

  public void testMap_map() throws Throwable {
    doCodeTest("""
                 Mark McGwire: {hr: 65, avg: 0.278}
                 Sammy Sosa: {
                     hr: 63,
                     avg: 0.288
                   }""");
  }

  public void testSample_log() throws Throwable {
    doCodeTest("""
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

  public void testSeq_seq() throws Throwable {
    doCodeTest("""
                 - [name        , hr, avg  ]
                 - [Mark McGwire, 65, 0.278]
                 - [Sammy Sosa  , 63, 0.288]""");
  }

  public void testSequence_mappings() throws Throwable {
    doCodeTest("""
                 -
                   name: Mark McGwire
                   hr:   65
                   avg:  0.278
                 -
                   name: Sammy Sosa
                   hr:   63
                   avg:  0.288""");
  }

  public void testBalance() throws Throwable {
    doCodeTest("""
                 runningTime: 150000
                 scenarios:
                     voice_bundle_change: {
                         dataCycling: true
                     }
                     smart_overview: {
                         dataCycling: true
                     }""");
  }

  public void testInterpolation() throws Throwable {
    doCodeTest("en:\n  foo: bar %{baz}");
  }

  public void testValue_injection() throws Throwable {
    doCodeTest("""
                 key:
                     one: 1 text
                     other: some {{count}} text""");
  }

  public void testSequence_idea76804() throws Throwable {
    doCodeTest("""
                 server:
                 - a
                 - b

                 server:
                   - a
                   - b""");
  }

  public void testMultiline_ruby16796() throws Throwable {
    doCodeTest("""
                 code:
                   src="keys/{{item}}"
                   mode=0600
                 with_items:
                   - "id_rsa.pub"
                 """);
  }

  public void testRuby17389() throws Throwable {
    doCodeTest("""
                 ---
                 foo: {}
                 bar: "baz\"""");
  }

  public void testRuby19105() throws Throwable {
    doCodeTest("""
                 'Fn::Join':
                   - ''
                   - - Ref: hostedZoneName
                     - a""");
  }

  public void testRuby15345() throws IOException {
    doCodeTest("""
                 - !qualified.class.name
                     propertyOne: bla bla
                     propertyWithOneSequence:
                         - first value
                     nextPropertyWithOneSequence:
                         - first value of another sequence""");
  }

  public void testHonestMultiline() throws Throwable {
    doCodeTest("""
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

  public void testEmptyMultiline() throws Throwable {
    doCodeTest("""
                 ---
                 foo: >
                 bar:
                   abc: def
                   ghi: >
                   jkl: mno
                 baz: qwe""");
  }

  public void testIncompleteKey() throws Throwable {
    doCodeTest("""
                 logging:
                   config: bla
                   index""");
  }

  public void testStringWithTag() throws IOException {
    doCodeTest("foo: ! \"tratata\"");
  }

  public void testIncompleteKeyWithWhitespace() throws IOException {
    doCodeTest("""
                 logging:
                   config:
                  \s
                  \s
                  \s
                  \s
                 """);
  }

  public void testShiftedMap() throws IOException {
    doCodeTest("    key: ttt\n" +
               "    ahahah: ppp");
  }

  public void testShiftedList() throws IOException {
    doCodeTest("    - item1\n" +
               "    - item2");
  }

  public void testExplicitMaps() {
    doTest(true);
  }

  public void testSpec2_27() {
    doTest(true);
  }

  public void testAnsibleRoleElkInit() {
    doTest(true);
  }

  public void testAnsibleRoleElkMain() {
    doTest(true);
  }

  public void testBlockMapping() {
    doTest(true);
  }

  public void testIncompleteKeyInHierarchy() {
    doTest(true);
  }

  public void testKeyValueWithEmptyLineAhead() {
    doTest(true);
  }

  public void testMultipleDocsWithMappings() {
    doTest(true);
  }

  public void testScalarsWithNewlines() {
    doTest(true);
  }

  public void testCommentInBlockScalarHeader() {
    doTest(true);
  }

  public void testErrorInBlockScalarHeader() {
    doTest(true);
  }

  public void testInlineMapWithBlockScalarValue()  {
    doTest(true);
  }

  public void testAliasInKey() { doTest(true); }

  public void testPlainMultilineScalarRuby21788() {
    doTest(true);
  }

  public void testAliasUseInArray() {
    doTest(true);
  }
}
