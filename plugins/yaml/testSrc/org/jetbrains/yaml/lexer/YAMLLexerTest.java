// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.Nullable;

public class YAMLLexerTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new YAMLFlexLexer();
  }

  @Override
  protected void doTest(String text, @Nullable String expected) {
    super.doTest(text, expected);
    checkCorrectRestart(text);
  }

  @Override
  protected String getDirPath() {
    return (PathManagerEx.getCommunityHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/lexer/data/")
      .substring(PathManager.getHomePath().length());
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

  public void testStringWithTag() {
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

  public void testRuby14738() {
    doTest();
  }

  public void testRuby14864() {
    doTest();
  }

  public void testRuby15402() {
    doTest();
  }

  public void testRuby17389() {
    doTest();
  }

  public void testRuby19105() {
    doTest();
  }

  public void testEmptyMultiline() {
    doTest();
  }

  public void testMultilineDoubleQuotedKey() {
    doTest();
  }

  public void testMultilineSingleQuotedKey() {
    doTest();
  }

  public void testMultilineDqLiteralWithEscapedNewlines() {
    doTest();
  }

  public void testSmallExplicitDocument() {
    doTest();
  }

  public void testSmallStream() {
    doTest();
  }

  public void testVerbatimTags() {
    doTest();
  }

  public void testTagShorthands() {
    doTest();
  }

  public void testOnlyScalars() {
    doTest();
  }

  public void testOnlyScalarNoDocument() {
    doTest();
  }

  public void testSingleQuotedEscapes() {
    doTest();
  }

  public void testUnicodeNewlines() {
    doTest();
  }

  public void testCcDocumentMarker1() {
    doTest();
  }

  public void testCcDocumentMarker2() {
    doTest();
  }

  public void testAnchorsAndAliases() {
    doTest();
  }

  public void testBlockScalarAfterDocMarker() {
    doTest();
  }

  public void testBlockScalarDocument() {
    doTest();
  }

  public void testBlockScalarZeroIndent() {
    doTest();
  }

  // Copy-paste from parser test
  public void testExplicitMaps() {
    doTest();
  }

  // NOTE: check invalid syntax
  public void testEarlyDocumentEnd() {
    doTest();
  }

  // NOTE: check invalid syntax
  public void testInlinedSequence() {
    doTest();
  }

  public void testExoticMultilinePlainScalar() {
    doTest();
  }

  public void testLonelyCloseBracket() {
    doTest();
  }

  // NOTE: check invalid syntax
  public void testShiftedSecondKey() {
    doTest();
  }

  public void testColonStartedTokens() {
    doTest();
  }

  public void testAliasInKey() { doTest(); }

  public void testCommentJustAfterSymbol() { doTest(); }

  private void doTest() {
    doFileTest("yml");
  }
}
