package org.jetbrains.yaml.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.LexerTestCase;

public class YAMLLexerTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new YAMLFlexLexer();
  }

  @Override
  protected String getDirPath() {
    return (PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/lexer/data/")
      .substring(PathManager.getHomePath().length());
  }

  @Override
  protected String getTestFileExt() {
    return "yml";
  }

  public void test2docs() {
    doFileTest();
  }

  public void testColorspage(){
    doFileTest();
  }

  public void testDocuments(){
    doFileTest();
  }

  public void testIndentation(){
    doFileTest();
  }

  public void testMap_between_seq(){
    doFileTest();
  }

  public void testMap_map(){
    doFileTest();
  }

  public void testQuoted_scalars(){
    doFileTest();
  }

  public void testSample_log(){
    doFileTest();
  }

  public void testSeq_seq(){
    doFileTest();
  }

  public void testSequence_mappings(){
    doFileTest();
  }

  public void testWrong_string_highlighting(){
    doFileTest();
  }

  public void testValue_injection(){
    doFileTest();
  }

  public void testValue_injection_2(){
    doFileTest();
  }

  public void testComma(){
    doFileTest();
  }

  public void testIndex(){
    doFileTest();
  }

  public void testKeydots(){
    doFileTest();
  }

  public void testColons74100(){
    doFileTest();
  }

  public void testOnlyyamlkey(){
    doFileTest();
  }

  public void testKey_parens(){
    doFileTest();
  }

  public void testKey_trailing_space(){
    doFileTest();
  }

  public void testComments(){
    doFileTest();
  }

  public void testNon_comment() {
    doFileTest();
  }

  public void testNon_comment2() {
    doFileTest();
  }

  public void testKey_with_brackets() {
    doFileTest();
  }

  public void testStrings() {
    doFileTest();
  }

  public void testStringWithTag() {
    doFileTest();
  }

  public void testNested_seqs() {
    doFileTest();
  }

  public void testMultiline_seq() {
    doFileTest();
  }

  public void testClosing_braces_in_value() {
    doFileTest();
  }

  public void testQuoted_keys() {
    doFileTest();
  }

  public void testTyped_scalar_list() {
    doFileTest();
  }

  public void testMultiline_ruby_16796() {
    doFileTest();
  }

  public void testRuby14738() {
    doFileTest();
  }

  public void testRuby14864() throws Throwable {
    doFileTest();
  }

  public void testRuby15402() throws Throwable {
    doFileTest();
  }

  public void testRuby17389() throws Throwable {
    doFileTest();
  }

  public void testEmptyMultiline() throws Throwable {
    doFileTest();
  }

  public void testMultilineDoubleQuotedKey() {
    doFileTest();
  }

  public void testMultilineSingleQuotedKey() {
    doFileTest();
  }

  public void testMultilineDqLiteralWithEscapedNewlines() {
    doFileTest();
  }

  public void testSmallExplicitDocument() {
    doFileTest();
  }

  public void testSmallStream() {
    doFileTest();
  }

  public void testVerbatimTags() {
    doFileTest();
  }

  public void testTagShorthands() {
    doFileTest();
  }

  public void testOnlyScalars() {
    doFileTest();
  }

  public void testOnlyScalarNoDocument() {
    doFileTest();
  }

  public void testSingleQuotedEscapes() {
    doFileTest();
  }

  public void testUnicodeNewlines() {
    doFileTest();
  }
}
