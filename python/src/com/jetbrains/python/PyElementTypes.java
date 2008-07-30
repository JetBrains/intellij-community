package com.jetbrains.python;

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.impl.stubs.*;
import com.jetbrains.python.psi.stubs.*;

public interface PyElementTypes {

  PyStubElementType<PyFunctionStub, PyFunction> FUNCTION_DECLARATION = new PyFunctionElementType();
  PyStubElementType<PyClassStub, PyClass> CLASS_DECLARATION = new PyClassElementType();
  PyStubElementType<PyParameterListStub, PyParameterList> PARAMETER_LIST = new PyParameterListElementType();

  TokenSet PARAMETER_LIST_SET = TokenSet.create(PARAMETER_LIST);  

  PyStubElementType<PyParameterStub, PyParameter> FORMAL_PARAMETER = new PyFormalParameterElementType();

  TokenSet FORMAL_PARAMETER_SET = TokenSet.create(FORMAL_PARAMETER);

  PyElementType DECORATED_FUNCTION_DECLARATION = new PyElementType("DECORATED_FUNCTION_DECLARATION", PyDecoratedFunctionImpl.class);
  PyElementType ARGUMENT_LIST = new PyElementType("ARGUMENT_LIST", PyArgumentListImpl.class);
  PyElementType IMPORT_ELEMENT = new PyElementType("IMPORT_ELEMENT", PyImportElementImpl.class);
  PyElementType STAR_IMPORT_ELEMENT = new PyElementType("STAR_IMPORT_ELEMENT", PyStarImportElementImpl.class);
  PyElementType EXCEPT_BLOCK = new PyElementType("EXCEPT_BLOCK", PyExceptBlockImpl.class);
  PyElementType PRINT_TARGET = new PyElementType("PRINT_TARGET", PyPrintTargetImpl.class);

  // Statements
  PyElementType EXPRESSION_STATEMENT = new PyElementType("EXPRESSION_STATEMENT", PyExpressionStatementImpl.class);
  PyElementType ASSIGNMENT_STATEMENT = new PyElementType("ASSIGNMENT_STATEMENT", PyAssignmentStatementImpl.class);
  PyElementType AUG_ASSIGNMENT_STATEMENT = new PyElementType("AUG_ASSIGNMENT_STATEMENT", PyAugAssignmentStatementImpl.class);
  PyElementType ASSERT_STATEMENT = new PyElementType("ASSERT_STATEMENT", PyAssertStatementImpl.class);
  PyElementType BREAK_STATEMENT = new PyElementType("BREAK_STATEMENT", PyBreakStatementImpl.class);
  PyElementType CONTINUE_STATEMENT = new PyElementType("CONTINUE_STATEMENT", PyContinueStatementImpl.class);
  PyElementType DEL_STATEMENT = new PyElementType("DEL_STATEMENT", PyDelStatementImpl.class);
  PyElementType EXEC_STATEMENT = new PyElementType("EXEC_STATEMENT", PyExecStatementImpl.class);
  PyElementType FOR_STATEMENT = new PyElementType("FOR_STATEMENT", PyForStatementImpl.class);
  PyElementType FROM_IMPORT_STATEMENT = new PyElementType("FROM_IMPORT_STATEMENT", PyFromImportStatementImpl.class);
  PyElementType GLOBAL_STATEMENT = new PyElementType("GLOBAL_STATEMENT", PyGlobalStatementImpl.class);
  PyElementType IMPORT_STATEMENT = new PyElementType("IMPORT_STATEMENT", PyImportStatementImpl.class);
  PyElementType IF_STATEMENT = new PyElementType("IF_STATEMENT", PyIfStatementImpl.class);
  PyElementType PASS_STATEMENT = new PyElementType("PASS_STATEMENT", PyPassStatementImpl.class);
  PyElementType PRINT_STATEMENT = new PyElementType("PRINT_STATEMENT", PyPrintStatementImpl.class);
  PyElementType RAISE_STATEMENT = new PyElementType("RAISE_STATEMENT", PyRaiseStatementImpl.class);
  PyElementType RETURN_STATEMENT = new PyElementType("RETURN_STATEMENT", PyReturnStatementImpl.class);
  PyElementType TRY_EXCEPT_STATEMENT = new PyElementType("TRY_EXCEPT_STATEMENT", PyTryExceptStatementImpl.class);
  PyElementType WITH_STATEMENT = new PyElementType("WITH_STATEMENT", PyWithStatementImpl.class);
  PyElementType WHILE_STATEMENT = new PyElementType("WHILE_STATEMENT", PyWhileStatementImpl.class);
  PyElementType STATEMENT_LIST = new PyElementType("STATEMENT_LIST", PyStatementListImpl.class);

  TokenSet STATEMENTS = TokenSet.create(EXPRESSION_STATEMENT, ASSIGNMENT_STATEMENT, AUG_ASSIGNMENT_STATEMENT, ASSERT_STATEMENT,
                                        BREAK_STATEMENT, CONTINUE_STATEMENT, DEL_STATEMENT, EXEC_STATEMENT, FOR_STATEMENT,
                                        FROM_IMPORT_STATEMENT, GLOBAL_STATEMENT, IMPORT_STATEMENT, IF_STATEMENT, PASS_STATEMENT,
                                        PRINT_STATEMENT, RAISE_STATEMENT, RETURN_STATEMENT, TRY_EXCEPT_STATEMENT, WITH_STATEMENT,
                                        WHILE_STATEMENT);

  TokenSet LOOPS = TokenSet.create(WHILE_STATEMENT, FOR_STATEMENT);

  // Expressions
  PyElementType EMPTY_EXPRESSION = new PyElementType("EMPTY_EXPRESSION", PyEmptyExpressionImpl.class);
  PyElementType REFERENCE_EXPRESSION = new PyElementType("REFERENCE_EXPRESSION", PyReferenceExpressionImpl.class);

  TokenSet REFERENCE_EXPRESSION_SET = TokenSet.create(REFERENCE_EXPRESSION);

  PyStubElementType<PyTargetExpressionStub, PyTargetExpression> TARGET_EXPRESSION = new PyTargetExpressionElementType();
  PyElementType INTEGER_LITERAL_EXPRESSION = new PyElementType("INTEGER_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);
  PyElementType FLOAT_LITERAL_EXPRESSION = new PyElementType("FLOAT_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);
  PyElementType IMAGINARY_LITERAL_EXPRESSION = new PyElementType("IMAGINARY_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl.class);
  PyElementType STRING_LITERAL_EXPRESSION = new PyElementType("STRING_LITERAL_EXPRESSION", PyStringLiteralExpressionImpl.class);
  PyElementType PARENTHESIZED_EXPRESSION = new PyElementType("PARENTHESIZED_EXPRESSION", PyParenthesizedExpressionImpl.class);
  PyElementType SUBSCRIPTION_EXPRESSION = new PyElementType("SUBSCRIPTION_EXPRESSION", PySubscriptionExpressionImpl.class);
  PyElementType SLICE_EXPRESSION = new PyElementType("SLICE_EXPRESSION", PySliceExpressionImpl.class);
  PyElementType BINARY_EXPRESSION = new PyElementType("BINARY_EXPRESSION", PyBinaryExpressionImpl.class);
  PyElementType PREFIX_EXPRESSION = new PyElementType("PREFIX_EXPRESSION", PyPrefixExpressionImpl.class);
  PyElementType CALL_EXPRESSION = new PyElementType("CALL_EXPRESSION", PyCallExpressionImpl.class);
  PyElementType LIST_LITERAL_EXPRESSION = new PyElementType("LIST_LITERAL_EXPRESSION", PyListLiteralExpressionImpl.class);
  PyElementType TUPLE_EXPRESSION = new PyElementType("TUPLE_EXPRESSION", PyTupleExpressionImpl.class);
  PyElementType KEYWORD_ARGUMENT_EXPRESSION = new PyElementType("KEYWORD_ARGUMENT_EXPRESSION", PyKeywordArgumentImpl.class);
  PyElementType STAR_ARGUMENT_EXPRESSION = new PyElementType("STAR_ARGUMENT_EXPRESSION", PyStarArgumentImpl.class);
  PyElementType LAMBDA_EXPRESSION = new PyElementType("LAMBDA_EXPRESSION", PyLambdaExpressionImpl.class);
  PyElementType LIST_COMP_EXPRESSION = new PyElementType("LIST_COMP_EXPRESSION", PyListCompExpressionImpl.class);
  PyElementType DICT_LITERAL_EXPRESSION = new PyElementType("DICT_LITERAL_EXPRESSION", PyDictLiteralExpressionImpl.class);
  PyElementType KEY_VALUE_EXPRESSION = new PyElementType("KEY_VALUE_EXPRESSION", PyKeyValueExpressionImpl.class);
  PyElementType REPR_EXPRESSION = new PyElementType("REPR_EXPRESSION", PyReprExpressionImpl.class);
  PyElementType GENERATOR_EXPRESSION = new PyElementType("GENERATOR_EXPRESSION", PyGeneratorExpressionImpl.class);
  PyElementType CONDITIONAL_EXPRESSION = new PyElementType("CONDITIONAL_EXPRESSION", PyConditionalExpressionImpl.class);
  PyElementType YIELD_EXPRESSION = new PyElementType("YIELD_EXPRESSION", PyYieldExpressionImpl.class);

  TokenSet EXPRESSIONS = TokenSet.create(EMPTY_EXPRESSION, REFERENCE_EXPRESSION, INTEGER_LITERAL_EXPRESSION, FLOAT_LITERAL_EXPRESSION,
                                         IMAGINARY_LITERAL_EXPRESSION, STRING_LITERAL_EXPRESSION, PARENTHESIZED_EXPRESSION,
                                         SUBSCRIPTION_EXPRESSION, SLICE_EXPRESSION, BINARY_EXPRESSION, PREFIX_EXPRESSION, CALL_EXPRESSION,
                                         LIST_LITERAL_EXPRESSION, TUPLE_EXPRESSION, KEYWORD_ARGUMENT_EXPRESSION, STAR_ARGUMENT_EXPRESSION,
                                         LAMBDA_EXPRESSION, LIST_COMP_EXPRESSION, DICT_LITERAL_EXPRESSION, KEY_VALUE_EXPRESSION,
                                         REPR_EXPRESSION, GENERATOR_EXPRESSION, CONDITIONAL_EXPRESSION, YIELD_EXPRESSION,
                                         TARGET_EXPRESSION);

  TokenSet STATEMENT_LISTS = TokenSet.create(STATEMENT_LIST);

  TokenSet BINARY_OPS = TokenSet.create(PyTokenTypes.OR_KEYWORD, PyTokenTypes.AND_KEYWORD, PyTokenTypes.LT, PyTokenTypes.GT,
                                        PyTokenTypes.OR, PyTokenTypes.XOR, PyTokenTypes.AND, PyTokenTypes.LTLT, PyTokenTypes.GTGT,
                                        PyTokenTypes.EQEQ, PyTokenTypes.GE, PyTokenTypes.LE, PyTokenTypes.NE, PyTokenTypes.NE_OLD,
                                        PyTokenTypes.IN_KEYWORD, PyTokenTypes.IS_KEYWORD, PyTokenTypes.NOT_KEYWORD, PyTokenTypes.PLUS,
                                        PyTokenTypes.MINUS, PyTokenTypes.MULT, PyTokenTypes.FLOORDIV, PyTokenTypes.DIV, PyTokenTypes.PERC);

}
