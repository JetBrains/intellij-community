package com.jetbrains.python.formatter;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.PyElementTypes.*;
import static com.jetbrains.python.PyTokenTypes.*;

/**
 * @author yole
 */
public class PythonFormattingModelBuilder implements FormattingModelBuilder, CustomFormattingModelBuilder {
  private static final boolean DUMP_FORMATTING_AST = false;

  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    if (DUMP_FORMATTING_AST) {
      ASTNode fileNode = element.getContainingFile().getNode();
      System.out.println("AST tree for " + element.getContainingFile().getName() + ":");
      printAST(fileNode, 0);
    }
    final CommonCodeStyleSettings codeStyleSettings = settings.getCommonSettings(PythonLanguage.getInstance());
    final PyBlock block =
      new PyBlock(element.getNode(), null, Indent.getNoneIndent(), null, codeStyleSettings,
                  createSpacingBuilder(settings));
    if (DUMP_FORMATTING_AST) {
      FormattingModelDumper.dumpFormattingModel(block, 2, System.out);
    }
    return FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(), block, settings);
  }

  protected SpacingBuilder createSpacingBuilder(CodeStyleSettings settings) {
    final IStubFileElementType file = PythonLanguage.getInstance().getFileElementType();
    final PyCodeStyleSettings pySettings = settings.getCustomSettings(PyCodeStyleSettings.class);
    final TokenSet STATEMENT_OR_DECLARATION = TokenSet.orSet(PythonDialectsTokenSetProvider.INSTANCE.getStatementTokens(),
                                                             CLASS_OR_FUNCTION);

    final CommonCodeStyleSettings commonSettings = settings.getCommonSettings(PythonLanguage.getInstance());
    return new SpacingBuilder(settings)
      .between(IMPORT_STATEMENTS, STATEMENT_OR_DECLARATION.minus(IMPORT_STATEMENTS)).blankLines(commonSettings.BLANK_LINES_AFTER_IMPORTS)
      .betweenInside(CLASS_OR_FUNCTION, CLASS_OR_FUNCTION, file).blankLines(pySettings.BLANK_LINES_BETWEEN_TOP_LEVEL_CLASSES_FUNCTIONS)
      .between(CLASS_DECLARATION, STATEMENT_OR_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_CLASS)
      .between(STATEMENT_OR_DECLARATION, CLASS_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_CLASS)
      .between(FUNCTION_DECLARATION, STATEMENT_OR_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_METHOD)
      .between(STATEMENT_OR_DECLARATION, FUNCTION_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_METHOD)
      .after(FUNCTION_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_METHOD)
      .after(CLASS_DECLARATION).blankLines(commonSettings.BLANK_LINES_AROUND_CLASS)
      .between(STATEMENT_OR_DECLARATION, STATEMENT_OR_DECLARATION).spacing(0, Integer.MAX_VALUE, 1, false, 1)
      
      .between(COLON, STATEMENT_LIST).spacing(1, Integer.MAX_VALUE, 0, true, 0)
      .afterInside(COLON, TokenSet.create(KEY_VALUE_EXPRESSION, LAMBDA_EXPRESSION)).spaceIf(pySettings.SPACE_AFTER_PY_COLON)

      .afterInside(GT, ANNOTATION).spaces(1)
      .betweenInside(MINUS, GT, ANNOTATION).none()
      .beforeInside(ANNOTATION, FUNCTION_DECLARATION).spaces(1)
      
      .between(TokenSet.not(TokenSet.create(LAMBDA_KEYWORD)), PARAMETER_LIST).spaceIf(commonSettings.SPACE_BEFORE_METHOD_PARENTHESES)
      
      .before(COLON).spaceIf(pySettings.SPACE_BEFORE_PY_COLON)
      .after(COMMA).spaceIf(commonSettings.SPACE_AFTER_COMMA)
      .before(COMMA).spaceIf(commonSettings.SPACE_BEFORE_COMMA)
      .before(SEMICOLON).spaceIf(commonSettings.SPACE_BEFORE_SEMICOLON)
      .withinPairInside(LPAR, RPAR, ARGUMENT_LIST).spaceIf(commonSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES)
      .before(LBRACKET).spaceIf(pySettings.SPACE_BEFORE_LBRACKET)

      .withinPair(LBRACE, RBRACE).spaceIf(commonSettings.SPACE_WITHIN_BRACES)
      .withinPair(LBRACKET, RBRACKET).spaceIf(commonSettings.SPACE_WITHIN_BRACKETS)

      .before(ARGUMENT_LIST).spaceIf(commonSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES)

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
      .around(IN_KEYWORD).spaces(1);
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
