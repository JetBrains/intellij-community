// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.lexer;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.SyntaxLexerTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixtures;
import com.intellij.yaml.syntax.YamlSyntaxDefinition;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.SyntaxLexerTestFixtureKt.syntaxLexerFixture;

@TestFixtures
public class YAMLLexerTest {
  private final TestFixture<SyntaxLexerTestFixture> lexer = syntaxLexerFixture(
    PathManagerEx.getCommunityHomePath() + "/plugins/yaml/backend/testData/org/jetbrains/yaml/lexer/data",
    () -> YamlSyntaxDefinition.INSTANCE.createLexer());

  @Test
  public void test2docs() {
    doTest();
  }

  @Test
  public void testColorspage(){
    doTest();
  }

  @Test
  public void testDocuments(){
    doTest();
  }

  @Test
  public void testIndentation(){
    doTest();
  }

  @Test
  public void testMap_between_seq(){
    doTest();
  }

  @Test
  public void testMap_map(){
    doTest();
  }

  @Test
  public void testQuoted_scalars(){
    doTest();
  }

  @Test
  public void testSample_log(){
    doTest();
  }

  @Test
  public void testSeq_seq(){
    doTest();
  }

  @Test
  public void testSequence_mappings(){
    doTest();
  }

  @Test
  public void testWrong_string_highlighting(){
    doTest();
  }

  @Test
  public void testValue_injection(){
    doTest();
  }

  @Test
  public void testValue_injection_2(){
    doTest();
  }

  @Test
  public void testComma(){
    doTest();
  }

  @Test
  public void testIndex(){
    doTest();
  }

  @Test
  public void testKeydots(){
    doTest();
  }

  @Test
  public void testColons74100(){
    doTest();
  }

  @Test
  public void testOnlyyamlkey(){
    doTest();
  }

  @Test
  public void testKey_parens(){
    doTest();
  }

  @Test
  public void testKey_trailing_space(){
    doTest();
  }

  @Test
  public void testComments(){
    doTest();
  }

  @Test
  public void testNon_comment() {
    doTest();
  }

  @Test
  public void testNon_comment2() {
    doTest();
  }

  @Test
  public void testKey_with_brackets() {
    doTest();
  }

  @Test
  public void testStrings() {
    doTest();
  }

  @Test
  public void testStringWithTag() {
    doTest();
  }

  @Test
  public void testNested_seqs() {
    doTest();
  }

  @Test
  public void testMultiline_seq() {
    doTest();
  }

  @Test
  public void testClosing_braces_in_value() {
    doTest();
  }

  @Test
  public void testQuoted_keys() {
    doTest();
  }

  @Test
  public void testTyped_scalar_list() {
    doTest();
  }

  @Test
  public void testMultiline_ruby_16796() {
    doTest();
  }

  @Test
  public void testRuby14738() {
    doTest();
  }

  @Test
  public void testRuby14864() {
    doTest();
  }

  @Test
  public void testRuby15402() {
    doTest();
  }

  @Test
  public void testRuby17389() {
    doTest();
  }

  @Test
  public void testRuby19105() {
    doTest();
  }

  @Test
  public void testEmptyMultiline() {
    doTest();
  }

  @Test
  public void testMultilineDoubleQuotedKey() {
    doTest();
  }

  @Test
  public void testMultilineSingleQuotedKey() {
    doTest();
  }

  @Test
  public void testMultilineDqLiteralWithEscapedNewlines() {
    doTest();
  }

  @Test
  public void testSmallExplicitDocument() {
    doTest();
  }

  @Test
  public void testSmallStream() {
    doTest();
  }

  @Test
  public void testVerbatimTags() {
    doTest();
  }

  @Test
  public void testTagShorthands() {
    doTest();
  }

  @Test
  public void testOnlyScalars() {
    doTest();
  }

  @Test
  public void testOnlyScalarNoDocument() {
    doTest();
  }

  @Test
  public void testSingleQuotedEscapes() {
    doTest();
  }

  @Test
  public void testUnicodeNewlines() {
    doTest();
  }

  @Test
  public void testCcDocumentMarker1() {
    doTest();
  }

  @Test
  public void testCcDocumentMarker2() {
    doTest();
  }

  @Test
  public void testAnchorsAndAliases() {
    doTest();
  }

  @Test
  public void testBlockScalarAfterDocMarker() {
    doTest();
  }

  @Test
  public void testBlockScalarDocument() {
    doTest();
  }

  @Test
  public void testBlockScalarZeroIndent() {
    doTest();
  }

  // Copy-paste from parser test
  @Test
  public void testExplicitMaps() {
    doTest();
  }

  @Test
  public void testExplicitMapsWithoutEmptyLine() {
    doTest();
  }

  // NOTE: check invalid syntax
  @Test
  public void testEarlyDocumentEnd() {
    doTest();
  }

  // NOTE: check invalid syntax
  @Test
  public void testInlinedSequence() {
    doTest();
  }

  @Test
  public void testExoticMultilinePlainScalar() {
    doTest();
  }

  @Test
  public void testLonelyCloseBracket() {
    doTest();
  }

  // NOTE: check invalid syntax
  @Test
  public void testShiftedSecondKey() {
    doTest();
  }

  @Test
  public void testColonStartedTokens() {
    doTest();
  }

  @Test
  public void testAliasInKey() { doTest(); }

  @Test
  public void testCommentJustAfterSymbol() { doTest(); }

  @Test
  public void testKeySpaceBeforeColon() { doTest(); }

  private void doTest() {
    lexer.get().doFileTest("yml");
  }
}
