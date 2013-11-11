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
    doFileTest("yml");
  }

  public void testColorspage(){
    doFileTest("yml");
  }

  public void testDocuments(){
    doFileTest("yml");
  }

  public void testIndentation(){
    doFileTest("yml");
  }

  public void testMap_between_seq(){
    doFileTest("yml");
  }

  public void testMap_map(){
    doFileTest("yml");
  }

  public void testQuoted_scalars(){
    doFileTest("yml");
  }

  public void testSample_log(){
    doFileTest("yml");
  }

  public void testSeq_seq(){
    doFileTest("yml");
  }

  public void testSequence_mappings(){
    doFileTest("yml");
  }

  public void testWrong_string_highlighting(){
    doFileTest("yml");
  }

  public void testValue_injection(){
    doFileTest("yml");
  }

  public void testValue_injection_2(){
    doFileTest("yml");
  }

  public void testComma(){
    doFileTest("yml");
  }

  public void testIndex(){
    doFileTest("yml");
  }

  public void testKeydots(){
    doFileTest("yml");
  }

  public void testColons74100(){
    doFileTest("yml");
  }

  public void testOnlyyamlkey(){
    doFileTest("yml");
  }

  public void testKey_parens(){
    doFileTest("yml");
  }

  public void testKey_trailing_space(){
    doFileTest("yml");
  }

  public void testComments(){
    doFileTest("yml");
  }

  public void testNon_comment() {
    doFileTest("yml");
  }

  public void testNon_comment2() {
    doFileTest("yml");
  }

  public void testKey_with_brackets() {
    doFileTest("yml");
  }

  public void testStrings() {
    doFileTest("yml");
  }

  public void testNested_seqs() {
    doFileTest("yml");
  }

  public void testMultiline_seq() {
    doFileTest("yml");
  }

  public void testClosing_braces_in_value() {
    doFileTest("yml");
  }

  public void testQuoted_keys() {
    doFileTest("yml");
  }
}
