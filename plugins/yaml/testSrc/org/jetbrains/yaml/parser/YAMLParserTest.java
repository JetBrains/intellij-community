package org.jetbrains.yaml.parser;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.ParsingTestCase;
import org.jetbrains.yaml.YAMLParserDefinition;

import java.io.IOException;

/**
 * @author oleg
 */
public class YAMLParserTest extends ParsingTestCase {

  public YAMLParserTest() {
    super("", "yml", new YAMLParserDefinition());
  }

  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/parser/data/";
  }

  public void test2docs() throws Throwable {
    doCodeTest("# Ranking of 1998 home runs\n" +
           "---\n" +
           "- Mark McGwire\n" +
           "- Sammy Sosa\n" +
           "- Ken Griffey\n" +
           "\n" +
           "# Team ranking\n" +
           "---\n" +
           "- Chicago Cubs\n" +
           "- St Louis Cardinals");
  }

  public void testIndentation() throws Throwable {
    doCodeTest("name: Mark McGwire\n" +
           "accomplishment: >\n" +
           "  Mark set a major league\n" +
           "  home run record in 1998.\n" +
           "stats: |\n" +
           "  65 Home Runs\n" +
           "  0.278 Batting Average");
  }

  public void testMap_between_seq() throws Throwable {
    doCodeTest("?\n" +
           "  - Detroit Tigers\n" +
           "  - Chicago cubs\n" +
           ":\n" +
           "  - 2001-07-23\n" +
           "\n" +
           "? [ New York Yankees,\n" +
           "    Atlanta Braves ]\n" +
           ": [ 2001-07-02, 2001-08-12,\n" +
           "    2001-08-14 ]");
  }

  public void testMap_map() throws Throwable {
    doCodeTest("Mark McGwire: {hr: 65, avg: 0.278}\n" +
           "Sammy Sosa: {\n" +
           "    hr: 63,\n" +
           "    avg: 0.288\n" +
           "  }");
  }

  public void testSample_log() throws Throwable {
    doCodeTest("Stack:\n" +
           "  - file: TopClass.py\n" +
           "    line: 23\n" +
           "    code: |\n" +
           "      x = MoreObject(\"345\\n\")\n" +
           "  - file: MoreClass.py\n" +
           "    line: 58\n" +
           "    code: |-\n" +
           "      foo = bar");
  }

  public void testSeq_seq() throws Throwable {
    doCodeTest("- [name        , hr, avg  ]\n" +
           "- [Mark McGwire, 65, 0.278]\n" +
           "- [Sammy Sosa  , 63, 0.288]");
  }

  public void testSequence_mappings() throws Throwable {
    doCodeTest("-\n" +
           "  name: Mark McGwire\n" +
           "  hr:   65\n" +
           "  avg:  0.278\n" +
           "-\n" +
           "  name: Sammy Sosa\n" +
           "  hr:   63\n" +
           "  avg:  0.288");
  }

  public void testBalance() throws Throwable {
    doCodeTest("runningTime: 150000\n" +
           "scenarios:\n" +
           "    voice_bundle_change: {\n" +
           "        dataCycling: true\n" +
           "    }\n" +
           "    smart_overview: {\n" +
           "        dataCycling: true\n" +
           "    }");
  }

  public void testInterpolation() throws Throwable {
    doCodeTest("en:\n  foo: bar %{baz}");
  }

  public void testValue_injection() throws Throwable {
    doCodeTest("key:\n" + "    one: 1 text\n" + "    other: some {{count}} text");
  }

  public void testSequence_idea76804() throws Throwable {
    doCodeTest("server:\n" +
           "- a\n" +
           "- b\n" +
           "\n" +
           "server:\n" +
           "  - a\n" +
           "  - b");
  }

  public void testMultiline_ruby16796() throws Throwable {
    doCodeTest("code:\n" +
               "  src=\"keys/{{item}}\"\n" +
               "  mode=0600\n" +
               "with_items:\n" +
               "  - \"id_rsa.pub\"\n");
  }

  public void testRuby17389() throws Throwable {
    doCodeTest("---\n" +
               "foo: {}\n" +
               "bar: \"baz\"");
  }

  public void testRuby19105() throws Throwable {
    doCodeTest("'Fn::Join':\n" +
               "  - ''\n" +
               "  - - Ref: hostedZoneName\n" +
               "    - a");
  }

  public void testRuby15345() throws IOException {
    doCodeTest("- !qualified.class.name\n" +
               "    propertyOne: bla bla\n" +
               "    propertyWithOneSequence:\n" +
               "        - first value\n" +
               "    nextPropertyWithOneSequence:\n" +
               "        - first value of another sequence");
  }

  public void testHonestMultiline() throws Throwable {
    doCodeTest("---\n" +
               "foo: >\n" +
               "  first text line\n" +
               "  second text line\n" +
               "\n" +
               "  baz: clazz\n" +
               "  - this is text\n" +
               "  - but looks like a list\n" +
               "  - indent tells.\n" +
               "bar: zoo");
  }

  public void testEmptyMultiline() throws Throwable {
    doCodeTest("---\n" +
               "foo: >\n" +
               "bar:\n" +
               "  abc: def\n" +
               "  ghi: >\n" +
               "  jkl: mno\n" +
               "baz: qwe");
  }

  public void testIncompleteKey() throws Throwable {
    doCodeTest("logging:\n" +
               "  config: bla\n" +
               "  index");
  }

  public void testStringWithTag() throws IOException {
    doCodeTest("foo: ! \"tratata\"");
  }

  public void testIncompleteKeyWithWhitespace() throws IOException {
    doCodeTest("logging:\n" +
               "  config:\n" +
               "  \n" +
               "  \n" +
               "  \n" +
               "  \n");
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
}
