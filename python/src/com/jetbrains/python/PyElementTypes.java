package com.jetbrains.python;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.*;

public class PyElementTypes {
  private PyElementTypes() {
  }

  public static final PyElementType FUNCTION_DECLARATION = new PyStubElementType("FUNCTION_DECLARATION", PyFunctionImpl.class);
  public static final PyElementType CLASS_DECLARATION = new PyStubElementType("CLASS_DECLARATION", PyClassImpl.class);
  public static final PyElementType PARAMETER_LIST = new PyStubElementType("PARAMETER_LIST", PyParameterListImpl.class);
  public static final PyElementType FORMAL_PARAMETER = new PyStubElementType("FORMAL_PARAMETER", PyParameterImpl.class);

  public static final PyElementType DECORATED_FUNCTION_DECLARATION = new PyElementType("DECORATED_FUNCTION_DECLARATION", PyDecoratedFunctionImpl.class);
  public static final PyElementType ARGUMENT_LIST = new PyElementType("ARGUMENT_LIST", PyArgumentListImpl.class);
  public static final PyElementType IMPORT_ELEMENT = new PyElementType("IMPORT_ELEMENT", PyImportElementImpl.class);
  public static final PyElementType EXCEPT_BLOCK = new PyElementType("EXCEPT_BLOCK", PyExceptBlockImpl.class);
  public static final PyElementType PRINT_TARGET = new PyElementType("PRINT_TARGET", PyPrintTargetImpl.class);

  // Statements
  public static final PyElementType EXPRESSION_STATEMENT = new PyElementType("EXPRESSION_STATEMENT", PyExpressionStatementImpl.class);
  public static final PyElementType ASSIGNMENT_STATEMENT = new PyElementType("ASSIGNMENT_STATEMENT", PyAssignmentStatementImpl.class);
  public static final PyElementType AUG_ASSIGNMENT_STATEMENT =
      new PyElementType("AUG_ASSIGNMENT_STATEMENT", PyAugAssignmentStatementImpl.class);
  public static final PyElementType ASSERT_STATEMENT = new PyElementType("ASSERT_STATEMENT", PyAssertStatementImpl.class);
  public static final PyElementType BREAK_STATEMENT = new PyElementType("BREAK_STATEMENT", PyBreakStatementImpl.class);
  public static final PyElementType CONTINUE_STATEMENT = new PyElementType("CONTINUE_STATEMENT", PyContinueStatementImpl.class);
  public static final PyElementType DEL_STATEMENT = new PyElementType("DEL_STATEMENT", PyDelStatementImpl.class);
  public static final PyElementType EXEC_STATEMENT = new PyElementType("EXEC_STATEMENT", PyExecStatementImpl.class);
  public static final PyElementType FOR_STATEMENT = new PyElementType("FOR_STATEMENT", PyForStatementImpl.class);
  public static final PyElementType FROM_IMPORT_STATEMENT = new PyElementType("FROM_IMPORT_STATEMENT", PyFromImportStatementImpl.class);
  public static final PyElementType GLOBAL_STATEMENT = new PyElementType("GLOBAL_STATEMENT", PyGlobalStatementImpl.class);
  public static final PyElementType IMPORT_STATEMENT = new PyElementType("IMPORT_STATEMENT", PyImportStatementImpl.class);
  public static final PyElementType IF_STATEMENT = new PyElementType("IF_STATEMENT", PyIfStatementImpl.class);
  public static final PyElementType PASS_STATEMENT = new PyElementType("PASS_STATEMENT", PyPassStatementImpl.class);
  public static final PyElementType PRINT_STATEMENT = new PyElementType("PRINT_STATEMENT", PyPrintStatementImpl.class);
  public static final PyElementType RAISE_STATEMENT = new PyElementType("RAISE_STATEMENT", PyRaiseStatementImpl.class);
  public static final PyElementType RETURN_STATEMENT = new PyElementType("RETURN_STATEMENT", PyReturnStatementImpl.class);
  public static final PyElementType TRY_EXCEPT_STATEMENT = new PyElementType("TRY_EXCEPT_STATEMENT", PyTryExceptStatementImpl.class);
  public static final PyElementType WITH_STATEMENT = new PyElementType("WITH_STATEMENT", PyWithStatementImpl.class);
  public static final PyElementType WHILE_STATEMENT = new PyElementType("WHILE_STATEMENT", PyWhileStatementImpl.class);
  public static final PyElementType STATEMENT_LIST = new PyElementType("STATEMENT_LIST", PyStatementListImpl.class);

  public static final TokenSet STATEMENTS = TokenSet.create(EXPRESSION_STATEMENT, ASSIGNMENT_STATEMENT, AUG_ASSIGNMENT_STATEMENT,
                                                            ASSERT_STATEMENT, BREAK_STATEMENT, CONTINUE_STATEMENT, DEL_STATEMENT,
                                                            EXEC_STATEMENT, FOR_STATEMENT, FROM_IMPORT_STATEMENT, GLOBAL_STATEMENT,
                                                            IMPORT_STATEMENT, IF_STATEMENT, PASS_STATEMENT, PRINT_STATEMENT,
                                                            RAISE_STATEMENT, RETURN_STATEMENT, TRY_EXCEPT_STATEMENT, WITH_STATEMENT,
                                                            WHILE_STATEMENT);

  public static final TokenSet LOOPS = TokenSet.create(WHILE_STATEMENT, FOR_STATEMENT);

  // Expressions
  public static final PyElementType EMPTY_EXPRESSION = new PyElementType("EMPTY_EXPRESSION", PyEmptyExpressionImpl.class);
  public static final PyElementType REFERENCE_EXPRESSION = new PyElementType("REFERENCE_EXPRESSION", PyReferenceExpressionImpl.class);
  public static final PyElementType TARGET_EXPRESSION = new PyElementType("TARGET_EXPRESSION", PyTargetExpressionImpl.class);
  public static final PyElementType INTEGER_LITERAL_EXPRESSION =
      new PyElementType("INTEGER_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);
  public static final PyElementType FLOAT_LITERAL_EXPRESSION =
      new PyElementType("FLOAT_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);
  public static final PyElementType IMAGINARY_LITERAL_EXPRESSION =
      new PyElementType("IMAGINARY_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);
  public static final PyElementType STRING_LITERAL_EXPRESSION =
      new PyElementType("STRING_LITERAL_EXPRESSION", PyStringLiteralExpressionImpl.class);
  public static final PyElementType PARENTHESIZED_EXPRESSION =
      new PyElementType("PARENTHESIZED_EXPRESSION", PyParenthesizedExpressionImpl.class);
  public static final PyElementType SUBSCRIPTION_EXPRESSION =
      new PyElementType("SUBSCRIPTION_EXPRESSION", PySubscriptionExpressionImpl.class);
  public static final PyElementType SLICE_EXPRESSION = new PyElementType("SLICE_EXPRESSION", PySliceExpressionImpl.class);
  public static final PyElementType BINARY_EXPRESSION = new PyElementType("BINARY_EXPRESSION", PyBinaryExpressionImpl.class);
  public static final PyElementType PREFIX_EXPRESSION = new PyElementType("PREFIX_EXPRESSION", PyPrefixExpressionImpl.class);
  public static final PyElementType CALL_EXPRESSION = new PyElementType("CALL_EXPRESSION", PyCallExpressionImpl.class);
  public static final PyElementType LIST_LITERAL_EXPRESSION =
      new PyElementType("LIST_LITERAL_EXPRESSION", PyListLiteralExpressionImpl.class);
  public static final PyElementType TUPLE_EXPRESSION = new PyElementType("TUPLE_EXPRESSION", PyTupleExpressionImpl.class);
  public static final PyElementType KEYWORD_ARGUMENT_EXPRESSION =
      new PyElementType("KEYWORD_ARGUMENT_EXPRESSION", PyKeywordArgumentImpl.class);
  public static final PyElementType STAR_ARGUMENT_EXPRESSION = new PyElementType("STAR_ARGUMENT_EXPRESSION", PyStarArgumentImpl.class);
  public static final PyElementType LAMBDA_EXPRESSION = new PyElementType("LAMBDA_EXPRESSION", PyLambdaExpressionImpl.class);
  public static final PyElementType LIST_COMP_EXPRESSION = new PyElementType("LIST_COMP_EXPRESSION", PyListCompExpressionImpl.class);
  public static final PyElementType DICT_LITERAL_EXPRESSION =
      new PyElementType("DICT_LITERAL_EXPRESSION", PyDictLiteralExpressionImpl.class);
  public static final PyElementType KEY_VALUE_EXPRESSION = new PyElementType("KEY_VALUE_EXPRESSION", PyKeyValueExpressionImpl.class);
  public static final PyElementType REPR_EXPRESSION = new PyElementType("REPR_EXPRESSION", PyReprExpressionImpl.class);
  public static final PyElementType GENERATOR_EXPRESSION = new PyElementType("GENERATOR_EXPRESSION", PyGeneratorExpressionImpl.class);
  public static final PyElementType CONDITIONAL_EXPRESSION = new PyElementType("CONDITIONAL_EXPRESSION", PyConditionalExpressionImpl.class);
  public static final PyElementType YIELD_EXPRESSION = new PyElementType("YIELD_EXPRESSION", PyYieldExpressionImpl.class);

  public static final TokenSet EXPRESSIONS = TokenSet.create(EMPTY_EXPRESSION, REFERENCE_EXPRESSION, INTEGER_LITERAL_EXPRESSION,
                                                             FLOAT_LITERAL_EXPRESSION, IMAGINARY_LITERAL_EXPRESSION,
                                                             STRING_LITERAL_EXPRESSION, PARENTHESIZED_EXPRESSION, SUBSCRIPTION_EXPRESSION,
                                                             SLICE_EXPRESSION, BINARY_EXPRESSION, PREFIX_EXPRESSION, CALL_EXPRESSION,
                                                             LIST_LITERAL_EXPRESSION, TUPLE_EXPRESSION, KEYWORD_ARGUMENT_EXPRESSION,
                                                             STAR_ARGUMENT_EXPRESSION, LAMBDA_EXPRESSION, LIST_COMP_EXPRESSION,
                                                             DICT_LITERAL_EXPRESSION, KEY_VALUE_EXPRESSION, REPR_EXPRESSION,
                                                             GENERATOR_EXPRESSION, CONDITIONAL_EXPRESSION, YIELD_EXPRESSION,
                                                             TARGET_EXPRESSION);

  public static final TokenSet STATEMENT_LISTS = TokenSet.create(STATEMENT_LIST);

  public static final TokenSet BINARY_OPS = TokenSet.create(PyTokenTypes.OR_KEYWORD, PyTokenTypes.AND_KEYWORD, PyTokenTypes.LT,
                                                            PyTokenTypes.GT, PyTokenTypes.OR, PyTokenTypes.XOR, PyTokenTypes.AND,
                                                            PyTokenTypes.LTLT, PyTokenTypes.GTGT, PyTokenTypes.EQEQ, PyTokenTypes.GE,
                                                            PyTokenTypes.LE, PyTokenTypes.NE, PyTokenTypes.NE_OLD, PyTokenTypes.IN_KEYWORD,
                                                            PyTokenTypes.IS_KEYWORD, PyTokenTypes.NOT_KEYWORD, PyTokenTypes.PLUS,
                                                            PyTokenTypes.MINUS, PyTokenTypes.MULT, PyTokenTypes.FLOORDIV, PyTokenTypes.DIV,
                                                            PyTokenTypes.PERC);

}
