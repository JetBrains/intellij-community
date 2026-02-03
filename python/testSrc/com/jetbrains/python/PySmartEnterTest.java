/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.lang.Language;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.List;

public class PySmartEnterTest extends PyTestCase {
  private static List<SmartEnterProcessor> getSmartProcessors(Language language) {
    return SmartEnterProcessors.INSTANCE.forKey(language);
  }

  public void doTest() {
    myFixture.configureByFile("codeInsight/smartEnter/" + getTestName(true) + ".py");
    final List<SmartEnterProcessor> processors = getSmartProcessors(PythonLanguage.getInstance());
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      final Editor editor = myFixture.getEditor();
      for (SmartEnterProcessor processor : processors) {
        processor.process(myFixture.getProject(), editor, myFixture.getFile());
      }
    });
    myFixture.checkResultByFile("codeInsight/smartEnter/" + getTestName(true) + "_after.py", true);
  }

  public void testIf() {
    doTest();
  }

  public void testWhile() {
    doTest();
  }

  public void testElif() {
    doTest();
  }

  public void testForFirst() {
    doTest();
  }

  public void testForSecond() {
    doTest();
  }

  public void testTry() {
    doTest();
  }

  public void testString() {
    doTest();
  }

  public void testDocstring() {
    doTest();
  }

  public void testDict() {
    doTest();
  }

  public void testParenthesized() {
    doTest();
  }

  public void testArgumentsFirst() {
    doTest();
  }

  public void testArgumentsSecond() {
    doTest();
  }

  public void testFunc() {
    doTest();
  }

  public void testClass() {
    doTest();
  }

  public void testComment() {
    doTest();
  }

  public void testPy891() {
    doTest();
  }

  public void testPy3209() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralInsideItem() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralAfterItem() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralInsideMultilineItem() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralOnLastLineOfMultilineItem() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralInsideCommentFollowingItem() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralEmptyLine() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralInsideCommentedLine() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralItemFollowedByComment() {
    doTest();
  }

  // PY-25001
  public void testMultilineListLiteralItemFollowsOpeningBracket() {
    doTest();
  }

  // PY-43053
  public void testMultilineListLiteralAfterClosingBracket() {
    doTest();
  }

  // PY-25001
  public void testMultilineSetLiteral() {
    doTest();
  }

  // PY-25001
  public void testMultilineTupleLiteralLastElement() {
    doTest();
  }

  // PY-25001
  public void testMultilineTupleLiteralIntermediateElement() {
    doTest();
  }

  // PY-25001
  public void testMultilineDictLiteralAfterKeyWithoutColon() {
    doTest();
  }

  // PY-25001
  public void testMultilineDictLiteralInsideKeyWithoutColon() {
    doTest();
  }

  // PY-25001
  public void testMultilineDictLiteralAfterKeyWithColonButNoValue() {
    doTest();
  }

  // PY-25001
  public void testMultilineDictLiteralValueOnSameLine() {
    doTest();
  }

  // PY-25001
  public void testMultilineDictLiteralValueOnOtherLine() {
    doTest();
  }

  // PY-25001
  public void testMultilineDictAfterUnpacking() {
    doTest();
  }

  // PY-43053
  public void testAfterFunctionDecoratedWithExpressionContainingCollectionLiteral() {
    doTest();
  }

  public void testDocRest() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    boolean oldStubOnEnter = codeInsightSettings.JAVADOC_STUB_ON_ENTER;
    codeInsightSettings.JAVADOC_STUB_ON_ENTER = true;
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.REST);
    try {
      doTest();
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
      codeInsightSettings.JAVADOC_STUB_ON_ENTER = oldStubOnEnter;
    }
  }

  public void testDocTypeRType() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    boolean oldStubOnEnter = codeInsightSettings.JAVADOC_STUB_ON_ENTER;
    codeInsightSettings.JAVADOC_STUB_ON_ENTER = true;
    PyCodeInsightSettings pyCodeInsightSettings = PyCodeInsightSettings.getInstance();
    boolean oldInsertType = pyCodeInsightSettings.INSERT_TYPE_DOCSTUB;
    pyCodeInsightSettings.INSERT_TYPE_DOCSTUB = true;
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getModule());
    documentationSettings.setFormat(DocStringFormat.REST);
    try {
      doTest();
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
      codeInsightSettings.JAVADOC_STUB_ON_ENTER = oldStubOnEnter;
      pyCodeInsightSettings.INSERT_TYPE_DOCSTUB = oldInsertType;
    }
  }

  // PY-15653
  public void testClassKeywordOnly() {
    doTest();
  }

  // PY-15653
  public void testClassKeywordAndThenWithBaseClasses() {
    doTest();
  }

  // PY-15653
  public void testDefKeywordOnly() {
    doTest();
  }


  // PY-15656
  public void testUnclosedParametersListAndTrailingEmptyLines() {
    doTest();
  }

  // PY-35163
  public void testMethodParameterNoDecorators() {
    doTest();
  }

  // PY-35163
  public void testMethodParameterClassMethod() {
    doTest();
  }

  // PY-35163
  public void testMethodParameterStaticMethod() {
    doTest();
  }

  // PY-35163
  public void testMethodParameterWithExistingParameters() {
    doTest();
  }

  // PY-35163
  public void testMethodParameterWithOpenBracket() {
    doTest();
  }

  // PY-12877
  public void testWithTargetOmitted() {
    doTest();
  }

  // PY-12877
  public void testWithTargetIncomplete() {
    doTest();
  }

  // PY-12877
  public void testWithExpressionMissing() {
    doTest();
  }

  public void testWithExpressionMissingNoSpaceAfterWithKeyword() {
    doTest();
  }

  // PY-12877
  public void testWithOnlyColonMissing() {
    doTest();
  }

  // PY-42200
  public void testWithParenthesizedWithItemsOnlyColonMissing() {
    doTest();
  }

  // PY-42200
  public void testWithParenthesizedWithItemsColonMissingAndTargetIncomplete() {
    doTest();
  }

  // PY-42200
  public void testWithParenthesizedWithItemsFirstTargetIncomplete() {
    doTest();
  }

  // PY-42200
  public void testWithParenthesizedWithItemsLastTargetIncomplete() {
    doTest();
  }

  // PY-42200
  public void testWithParenthesizedWithItemsNothingToFix() {
    doTest();
  }

  // PY-9209
  public void testSpaceInsertedAfterHashSignInComment() {
    doTest();
  }

  // PY-16765
  public void testGoogleDocStringColonAndIndentAfterSection() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, this::doTest);
  }

  // PY-16765
  public void testGoogleDocStringIndentAfterSection() {
    runWithDocStringFormat(DocStringFormat.GOOGLE, this::doTest);
  }

  // PY-16765
  public void testGoogleDocStringIndentAfterSectionCustomIndent() {
    getIndentOptions().INDENT_SIZE = 2;
    runWithDocStringFormat(DocStringFormat.GOOGLE, this::doTest);
  }

  // PY-19279
  public void testColonAfterReturnTypeAnnotation() {
    runWithLanguageLevel(LanguageLevel.PYTHON34, this::doTest);
  }

  // PY-48014
  public void testColonAndFirstClauseAfterEmptyMatchStatementWithSubjectCaretOnSubject() {
    doTest();
  }

  // PY-48014
  public void testColonAndFirstClauseAfterEmptyMatchStatementWithSubjectCaretOnMatch() {
    doTest();
  }

  // PY-48014
  public void testColonAndFirstClauseAfterEmptyMatchStatementWithSubjectLookingLikeBinaryExpression() {
    doTest();
  }

  // PY-48014
  public void testColonAndFirstClauseAfterEmptyMatchStatementWithSubjectLookingLikeCallExpression() {
    doTest();
  }

  // PY-48014
  public void testNothingAfterUnambiguousExpressionStartingWithMatch() {
    doTest();
  }

  // PY-48014
  public void testColonAndFirstClauseAfterEmptyMatchStatementWithSubjectCustomIndent() {
    getIndentOptions().INDENT_SIZE = 2;
    doTest();
  }

  // PY-48014
  public void testColonAfterEmptyMatchStatementWithoutSubject() {
    doTest();
  }

  // PY-48014
  public void testOnlyCaretMoveAfterMatchStatementWithColonWithoutSubject() {
    doTest();
  }

  // PY-48014
  public void testFirstClauseAfterEmptyMatchStatementWithSubjectAndColon() {
    doTest();
  }

  // PY-48014
  public void testLineBreakAndIndentAfterNonEmptyMatchStatementWithSubjectAndColon() {
    doTest();
  }

  // PY-48014
  public void testNoFirstClauseInMatchStatementWithIncompleteStatementInside() {
    doTest();
  }

  // PY-48014
  public void testColonAndIndentAfterCaseClauseWithPattern() {
    doTest();
  }

  // PY-48014
  public void testColonAfterCaseClauseWithoutPattern() {
    doTest();
  }

  // PY-48014
  public void testOnlyCaretMoveAfterCaseClauseWithColonWithoutPattern() {
    doTest();
  }

  // PY-48014
  public void testIndentAfterCaseClauseWithPatternAndColon() {
    doTest();
  }

  // PY-48014
  public void testColonAndIndentAfterCaseClauseWithPatternAndGuardCondition() {
    doTest();
  }

  // PY-48014
  public void testColonAfterCaseClauseWithPatternWithoutGuardCondition() {
    doTest();
  }

  // PY-48014
  public void testOnlyCaretMoveAfterCaseClauseWithGuardWithoutPattern() {
    doTest();
  }

  // PY-48014
  public void testOnlyCaretMoveAfterCaseClauseWithPatternAndColonWithoutGuardCondition() {
    doTest();
  }

  // PY-48014
  public void testColonAfterCaseClauseWithoutPatternWithoutGuardCondition() {
    doTest();
  }

  // PY-48014
  public void testColonAfterCaseClauseWithoutPatternWithGuardCondition() {
    doTest();
  }

  // PY-49785
  public void testColonAfterIntermediateCaseClause() {
    doTest();
  }

  // PY-49785
  public void testColonAndIndentAfterIntermediateCaseClause() {
    doTest();
  }

  // PY-49785
  public void testColonAfterFinalCaseClauseWithPrecedingIncompleteCaseClause() {
    doTest();
  }
}
