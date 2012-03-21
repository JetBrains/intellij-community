package com.jetbrains.python;

import com.intellij.psi.tree.TokenSet;
import static com.jetbrains.python.PyElementTypes.*;
import static com.jetbrains.python.PyTokenTypes.*;

/**
 * @author vlan
 */
public class PythonTokenSetContributor implements PythonDialectsTokenSetContributor {
  @Override
  public TokenSet getStatementTokens() {
    return TokenSet.create(EXPRESSION_STATEMENT, ASSIGNMENT_STATEMENT, AUG_ASSIGNMENT_STATEMENT, ASSERT_STATEMENT,
                           BREAK_STATEMENT, CONTINUE_STATEMENT, DEL_STATEMENT, EXEC_STATEMENT, FOR_STATEMENT,
                           FROM_IMPORT_STATEMENT, GLOBAL_STATEMENT, IMPORT_STATEMENT, IF_STATEMENT, PASS_STATEMENT,
                           PRINT_STATEMENT, RAISE_STATEMENT, RETURN_STATEMENT, TRY_EXCEPT_STATEMENT, WITH_STATEMENT,
                           WHILE_STATEMENT, NONLOCAL_STATEMENT, CLASS_DECLARATION, FUNCTION_DECLARATION);
  }

  @Override
  public TokenSet getExpressionTokens() {
    return TokenSet.create(EMPTY_EXPRESSION, REFERENCE_EXPRESSION, INTEGER_LITERAL_EXPRESSION, FLOAT_LITERAL_EXPRESSION,
                           IMAGINARY_LITERAL_EXPRESSION, STRING_LITERAL_EXPRESSION, PARENTHESIZED_EXPRESSION,
                           SUBSCRIPTION_EXPRESSION, SLICE_EXPRESSION, BINARY_EXPRESSION, PREFIX_EXPRESSION, CALL_EXPRESSION,
                           LIST_LITERAL_EXPRESSION, TUPLE_EXPRESSION, KEYWORD_ARGUMENT_EXPRESSION, STAR_ARGUMENT_EXPRESSION,
                           LAMBDA_EXPRESSION, LIST_COMP_EXPRESSION, DICT_LITERAL_EXPRESSION, KEY_VALUE_EXPRESSION,
                           REPR_EXPRESSION, GENERATOR_EXPRESSION, CONDITIONAL_EXPRESSION, YIELD_EXPRESSION,
                           TARGET_EXPRESSION, NONE_LITERAL_EXPRESSION, BOOL_LITERAL_EXPRESSION,
                           SET_LITERAL_EXPRESSION, SET_COMP_EXPRESSION, DICT_COMP_EXPRESSION, STAR_EXPRESSION);
  }

  @Override
  public TokenSet getNameDefinerTokens() {
    // FROM_IMPORT_STATEMENT is not exactly a NameDefiner but needed anyway in mypackage/__init__.py, 'from mypackage.foo import bar' makes
    // 'foo' name visible
    return TokenSet.create(PyElementTypes.STAR_IMPORT_ELEMENT, PyElementTypes.IMPORT_ELEMENT, PyElementTypes.CLASS_DECLARATION,
                           PyElementTypes.GLOBAL_STATEMENT, PyElementTypes.GENERATOR_EXPRESSION, PyElementTypes.DICT_COMP_EXPRESSION,
                           PyElementTypes.LIST_COMP_EXPRESSION, PyElementTypes.SET_COMP_EXPRESSION, PyElementTypes.WITH_STATEMENT,
                           PyElementTypes.FUNCTION_DECLARATION, PyElementTypes.ASSIGNMENT_STATEMENT, PyElementTypes.EXCEPT_PART,
                           PyElementTypes.FOR_STATEMENT,
                           PyElementTypes.FROM_IMPORT_STATEMENT);
  }

  @Override
  public TokenSet getKeywordTokens() {
    return TokenSet.create(
      AND_KEYWORD, AS_KEYWORD, ASSERT_KEYWORD, BREAK_KEYWORD, CLASS_KEYWORD,
      CONTINUE_KEYWORD, DEF_KEYWORD, DEL_KEYWORD, ELIF_KEYWORD, ELSE_KEYWORD,
      EXCEPT_KEYWORD, EXEC_KEYWORD, FINALLY_KEYWORD, FOR_KEYWORD,
      FROM_KEYWORD,
      GLOBAL_KEYWORD, IF_KEYWORD, IMPORT_KEYWORD, IN_KEYWORD, IS_KEYWORD,
      LAMBDA_KEYWORD, NOT_KEYWORD, OR_KEYWORD, PASS_KEYWORD, PRINT_KEYWORD,
      RAISE_KEYWORD, RETURN_KEYWORD, TRY_KEYWORD, WITH_KEYWORD, WHILE_KEYWORD,
      YIELD_KEYWORD,
      NONE_KEYWORD, TRUE_KEYWORD, FALSE_KEYWORD, NONLOCAL_KEYWORD, DEBUG_KEYWORD);
  }
}
