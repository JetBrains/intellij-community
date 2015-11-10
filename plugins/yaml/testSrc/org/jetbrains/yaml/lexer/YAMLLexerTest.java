package org.jetbrains.yaml.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;

public class YAMLLexerTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new YAMLFlexLexer();
  }

  @Override
  protected String getDirPath() {
    return "plugins/yaml/testSrc/org/jetbrains/yaml/lexer/data";
  }

  public void test2docs() {
    doTest();
  }

  public void testColorspage(){
    doTest();
  }

  public void testDocuments(){
    doTest();
  }

  public void testIndentation(){
    doTest();
  }

  public void testMap_between_seq(){
    doTest();
  }

  public void testMap_map(){
    doTest();
  }

  public void testQuoted_scalars(){
    doTest();
  }

  public void testSample_log(){
    doTest();
  }

  public void testSeq_seq(){
    doTest();
  }

  public void testSequence_mappings(){
    doTest();
  }

  public void testWrong_string_highlighting(){
    doTest();
  }

  public void testValue_injection(){
    doTest();
  }

  public void testValue_injection_2(){
    doTest();
  }

  public void testComma(){
    doTest();
  }

  public void testIndex(){
    doTest();
  }

  public void testKeydots(){
    doTest();
  }

  public void testColons74100(){
    doTest();
  }

  public void testOnlyyamlkey(){
    doTest();
  }

  public void testKey_parens(){
    doTest();
  }

  public void testKey_trailing_space(){
    doTest();
  }

  public void testComments(){
    doTest();
  }

  public void testNon_comment() {
    doTest();
  }

  public void testNon_comment2() {
    doTest();
  }

  public void testKey_with_brackets() {
    doTest();
  }

  public void testStrings() {
    doTest();
  }

  public void testNested_seqs() {
    doTest();
  }

  public void testMultiline_seq() {
    doTest();
  }

  public void testClosing_braces_in_value() {
    doTest();
  }

  public void testQuoted_keys() {
    doTest();
  }

  public void testTyped_scalar_list() {
    doTest();
  }

  public void testMultiline_ruby_16796() {
    doTest();
  }

  public void testRuby14864() throws Throwable {
    doTest();
  }

  public void testRuby17389() throws Throwable {
    doTest();
  }

  public void testEmptyMultiline() throws Throwable {
    doTest();
  }

  public void testMultilineDoubleQuotedKey() {
    doTest();
  }

  public void testMultilineSingleQuotedKey() {
    doTest();
  }

  private void doTest() {
    doFileTest("yml");
  }
}
