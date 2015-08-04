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
package com.jetbrains.python.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PyElementTypes.*;
import static com.jetbrains.python.PyTokenTypes.*;

/**
 * @author yole
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class PythonFormattingModelBuilder implements FormattingModelBuilderEx, CustomFormattingModelBuilder {
  private static final boolean DUMP_FORMATTING_AST = false;
  public static final TokenSet STATEMENT_OR_DECLARATION = PythonDialectsTokenSetProvider.INSTANCE.getStatementTokens();

  @NotNull
  @Override
  public FormattingModel createModel(@NotNull PsiElement element,
                                     @NotNull CodeStyleSettings settings,
                                     @NotNull FormattingMode mode) {
    if (DUMP_FORMATTING_AST) {
      ASTNode fileNode = element.getContainingFile().getNode();
      System.out.println("AST tree for " + element.getContainingFile().getName() + ":");
      printAST(fileNode, 0);
    }
    final PyBlockContext context = new PyBlockContext(settings, createSpacingBuilder(settings), mode);
    final PyBlock block = new PyBlock(null, element.getNode(), null, Indent.getNoneIndent(), null, context);
    if (DUMP_FORMATTING_AST) {
      FormattingModelDumper.dumpFormattingModel(block, 2, System.out);
    }
    return FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(), block, settings);
  }

  @Nullable
  @Override
  public CommonCodeStyleSettings.IndentOptions getIndentOptionsToUse(@NotNull PsiFile file,
                                                                     @NotNull FormatTextRanges ranges,
                                                                     @NotNull CodeStyleSettings settings) {
    return null;
  }

  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    return createModel(element, settings, FormattingMode.REFORMAT);
  }

  protected SpacingBuilder createSpacingBuilder(CodeStyleSettings settings) {
    final IFileElementType file = LanguageParserDefinitions.INSTANCE.forLanguage(PythonLanguage.getInstance()).getFileNodeType();
    final PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);

    final CommonCodeStyleSettings commonSettings = settings.getCommonSettings(PythonLanguage.getInstance());
    return new SpacingBuilder(commonSettings)
      .between(CLASS_DECLARATION, STATEMENT_OR_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_CLASS)
      .between(STATEMENT_OR_DECLARATION, CLASS_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_CLASS)
      .between(FUNCTION_DECLARATION, STATEMENT_OR_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_METHOD)
      .between(STATEMENT_OR_DECLARATION, FUNCTION_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_METHOD)
      .after(FUNCTION_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_METHOD)
      .after(CLASS_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_CLASS)
      // Remove excess blank lines between imports, because ImportOptimizer gets rid of them anyway.
      // Empty lines between import groups are handles in PyBlock#getSpacing
      .between(IMPORT_STATEMENTS, IMPORT_STATEMENTS).spacing(0, Integer.MAX_VALUE, 1, false, 0)
      .between(STATEMENT_OR_DECLARATION, STATEMENT_OR_DECLARATION).spacing(0, Integer.MAX_VALUE, 1, false, 1)

      .between(COLON, STATEMENT_LIST).spacing(1, Integer.MAX_VALUE, 0, true, 0)
      .afterInside(COLON, TokenSet.create(KEY_VALUE_EXPRESSION, LAMBDA_EXPRESSION)).spaceIf(pySettings.SPACE_AFTER_PY_COLON)

      .afterInside(GT, ANNOTATION).spaces(1)
      .betweenInside(MINUS, GT, ANNOTATION).none()
      .beforeInside(ANNOTATION, FUNCTION_DECLARATION).spaces(1)
      .beforeInside(ANNOTATION, NAMED_PARAMETER).none()
      .afterInside(COLON, ANNOTATION).spaces(1)
      .afterInside(RARROW, ANNOTATION).spaces(1)

      .between(allButLambda(), PARAMETER_LIST).spaceIf(commonSettings.SPACE_BEFORE_METHOD_PARENTHESES)

      .before(COLON).spaceIf(pySettings.SPACE_BEFORE_PY_COLON)
      .after(COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA)
      .before(COMMA).spaceIf(commonSettings.SPACE_BEFORE_COMMA)
      .between(FROM_KEYWORD, DOT).spaces(1)
      .between(DOT, IMPORT_KEYWORD).spaces(1)
      .around(DOT).spaces(0)
      .aroundInside(AT, DECORATOR_CALL).none()
      .before(SEMICOLON).spaceIf(commonSettings.SPACE_BEFORE_SEMICOLON)
      .withinPairInside(LPAR, RPAR, ARGUMENT_LIST).spaceIf(commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES)
      .withinPairInside(LPAR, RPAR, PARAMETER_LIST).spaceIf(commonSettings.SPACE_WITHIN_METHOD_PARENTHESES)
      .withinPairInside(LPAR, RPAR, FROM_IMPORT_STATEMENT).spaces(0)
      .withinPairInside(LPAR, RPAR, GENERATOR_EXPRESSION).spaces(0)
      .withinPairInside(LPAR, RPAR, PARENTHESIZED_EXPRESSION).spaces(0)
      .before(LBRACKET).spaceIf(pySettings.SPACE_BEFORE_LBRACKET)

      .afterInside(LBRACE, DICT_LITERAL_EXPRESSION).spaceIf(pySettings.SPACE_WITHIN_BRACES, pySettings.DICT_NEW_LINE_AFTER_LEFT_BRACE)
      .beforeInside(RBRACE, DICT_LITERAL_EXPRESSION).spaceIf(pySettings.SPACE_WITHIN_BRACES, pySettings.DICT_NEW_LINE_BEFORE_RIGHT_BRACE)
      .withinPair(LBRACE, RBRACE).spaceIf(pySettings.SPACE_WITHIN_BRACES)
      .withinPair(LBRACKET, RBRACKET).spaceIf(commonSettings.SPACE_WITHIN_BRACKETS)

      .before(ARGUMENT_LIST).spaceIf(commonSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES)

      .around(DECORATOR_CALL).spacing(1, Integer.MAX_VALUE, 0, true, 0)
      .after(DECORATOR_LIST).spacing(1, Integer.MAX_VALUE, 0, true, 0)

      .aroundInside(EQ, ASSIGNMENT_STATEMENT).spaceIf(commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)
      .aroundInside(EQ, NAMED_PARAMETER).spaceIf(pySettings.SPACE_AROUND_EQ_IN_NAMED_PARAMETER)
      .aroundInside(EQ, KEYWORD_ARGUMENT_EXPRESSION).spaceIf(pySettings.SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT)

      .around(AUG_ASSIGN_OPERATIONS).spaceIf(commonSettings.SPACE_AROUND_ASSIGNMENT_OPERATORS)
      .aroundInside(ADDITIVE_OPERATIONS, BINARY_EXPRESSION).spaceIf(commonSettings.SPACE_AROUND_ADDITIVE_OPERATORS)
      .aroundInside(MULTIPLICATIVE_OR_EXP, STAR_PARAMETERS).none()
      .around(MULTIPLICATIVE_OR_EXP).spaceIf(commonSettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS)
      .around(SHIFT_OPERATIONS).spaceIf(commonSettings.SPACE_AROUND_SHIFT_OPERATORS)
      .around(BITWISE_OPERATIONS).spaceIf(commonSettings.SPACE_AROUND_BITWISE_OPERATORS)
      .around(EQUALITY_OPERATIONS).spaceIf(commonSettings.SPACE_AROUND_EQUALITY_OPERATORS)
      .around(RELATIONAL_OPERATIONS).spaceIf(commonSettings.SPACE_AROUND_RELATIONAL_OPERATORS)
      .around(SINGLE_SPACE_KEYWORDS).spaces(1);
  }

  // should be all keywords?
  private static final TokenSet SINGLE_SPACE_KEYWORDS = TokenSet.create(IN_KEYWORD, AND_KEYWORD, OR_KEYWORD, IS_KEYWORD,
                                                                        IF_KEYWORD, ELIF_KEYWORD, FOR_KEYWORD, RETURN_KEYWORD, RAISE_KEYWORD,
                                                                        ASSERT_KEYWORD, CLASS_KEYWORD, DEF_KEYWORD, DEL_KEYWORD,
                                                                        EXEC_KEYWORD, GLOBAL_KEYWORD, IMPORT_KEYWORD, LAMBDA_KEYWORD,
                                                                        NOT_KEYWORD, WHILE_KEYWORD, YIELD_KEYWORD);

  private static TokenSet allButLambda() {
    final PythonLanguage pythonLanguage = PythonLanguage.getInstance();
    return TokenSet.create(IElementType.enumerate(new IElementType.Predicate() {
      @Override
      public boolean matches(@NotNull IElementType type) {
        return type != LAMBDA_KEYWORD && type.getLanguage().isKindOf(pythonLanguage);
      }
    }));
  }

  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }

  private static void printAST(ASTNode node, int indent) {
    while (node != null) {
      for (int i = 0; i < indent; i++) {
        System.out.print(" ");
      }
      System.out.println(node.toString() + " " + node.getTextRange().toString());
      printAST(node.getFirstChildNode(), indent + 2);
      node = node.getTreeNext();
    }
  }

  public boolean isEngagedToFormat(PsiElement context) {
    PsiFile file = context.getContainingFile();
    return file != null && file.getLanguage() == PythonLanguage.getInstance();
  }
}
