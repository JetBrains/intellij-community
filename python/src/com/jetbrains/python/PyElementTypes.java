/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import com.jetbrains.python.psi.stubs.*;

public interface PyElementTypes {

  PyStubElementType<PyFunctionStub, PyFunction> FUNCTION_DECLARATION = PyStubElementTypes.FUNCTION_DECLARATION;
  PyStubElementType<PyClassStub, PyClass> CLASS_DECLARATION = PyStubElementTypes.CLASS_DECLARATION;
  PyStubElementType<PyParameterListStub, PyParameterList> PARAMETER_LIST = PyStubElementTypes.PARAMETER_LIST;

  PyStubElementType<PyDecoratorListStub, PyDecoratorList> DECORATOR_LIST = PyStubElementTypes.DECORATOR_LIST;

  PyStubElementType<PyNamedParameterStub, PyNamedParameter> NAMED_PARAMETER = PyStubElementTypes.NAMED_PARAMETER;
  PyStubElementType<PyTupleParameterStub, PyTupleParameter> TUPLE_PARAMETER = PyStubElementTypes.TUPLE_PARAMETER;
  PyStubElementType<PySlashParameterStub, PySlashParameter> SLASH_PARAMETER = PyStubElementTypes.SLASH_PARAMETER;
  PyStubElementType<PySingleStarParameterStub, PySingleStarParameter> SINGLE_STAR_PARAMETER = PyStubElementTypes.SINGLE_STAR_PARAMETER;

  PyStubElementType<PyDecoratorStub, PyDecorator> DECORATOR_CALL = PyStubElementTypes.DECORATOR_CALL;

  PyStubElementType<PyImportElementStub, PyImportElement> IMPORT_ELEMENT = PyStubElementTypes.IMPORT_ELEMENT;

  PyStubElementType<PyAnnotationStub, PyAnnotation> ANNOTATION = PyStubElementTypes.ANNOTATION;

  PyStubElementType<PyStarImportElementStub, PyStarImportElement> STAR_IMPORT_ELEMENT = PyStubElementTypes.STAR_IMPORT_ELEMENT;
  PyStubElementType<PyExceptPartStub, PyExceptPart> EXCEPT_PART = PyStubElementTypes.EXCEPT_PART;

  PyStubElementType<PyFromImportStatementStub, PyFromImportStatement> FROM_IMPORT_STATEMENT = PyStubElementTypes.FROM_IMPORT_STATEMENT;
  PyStubElementType<PyImportStatementStub, PyImportStatement> IMPORT_STATEMENT = PyStubElementTypes.IMPORT_STATEMENT;

  PyStubElementType<PyTargetExpressionStub, PyTargetExpression> TARGET_EXPRESSION = PyStubElementTypes.TARGET_EXPRESSION;

  TokenSet PARAMETER_LIST_SET = TokenSet.create(PARAMETER_LIST);

  TokenSet FORMAL_PARAMETER_SET = TokenSet.create(NAMED_PARAMETER);

  PyElementType ARGUMENT_LIST = new PyElementType("ARGUMENT_LIST", PyArgumentListImpl::new);

  PyElementType PRINT_TARGET = new PyElementType("PRINT_TARGET", PyPrintTargetImpl::new);
  PyElementType DECORATOR = new PyElementType("DECORATOR", PyDecoratorImpl::new);

  // Statements
  PyElementType EXPRESSION_STATEMENT = new PyElementType("EXPRESSION_STATEMENT", PyExpressionStatementImpl::new);
  PyElementType ASSIGNMENT_STATEMENT = new PyElementType("ASSIGNMENT_STATEMENT", PyAssignmentStatementImpl::new);
  PyElementType AUG_ASSIGNMENT_STATEMENT = new PyElementType("AUG_ASSIGNMENT_STATEMENT", PyAugAssignmentStatementImpl::new);
  PyElementType ASSERT_STATEMENT = new PyElementType("ASSERT_STATEMENT", PyAssertStatementImpl::new);
  PyElementType BREAK_STATEMENT = new PyElementType("BREAK_STATEMENT", PyBreakStatementImpl::new);
  PyElementType CONTINUE_STATEMENT = new PyElementType("CONTINUE_STATEMENT", PyContinueStatementImpl::new);
  PyElementType DEL_STATEMENT = new PyElementType("DEL_STATEMENT", PyDelStatementImpl::new);
  PyElementType EXEC_STATEMENT = new PyElementType("EXEC_STATEMENT", PyExecStatementImpl::new);
  PyElementType FOR_STATEMENT = new PyElementType("FOR_STATEMENT", PyForStatementImpl::new);
  PyElementType TYPE_DECLARATION_STATEMENT = new PyElementType("TYPE_DECLARATION_STATEMENT", PyTypeDeclarationStatementImpl::new);

  PyElementType GLOBAL_STATEMENT = new PyElementType("GLOBAL_STATEMENT", PyGlobalStatementImpl::new);
  PyElementType IF_STATEMENT = new PyElementType("IF_STATEMENT", PyIfStatementImpl::new);
  PyElementType PASS_STATEMENT = new PyElementType("PASS_STATEMENT", PyPassStatementImpl::new);
  PyElementType PRINT_STATEMENT = new PyElementType("PRINT_STATEMENT", PyPrintStatementImpl::new);
  PyElementType RAISE_STATEMENT = new PyElementType("RAISE_STATEMENT", PyRaiseStatementImpl::new);
  PyElementType RETURN_STATEMENT = new PyElementType("RETURN_STATEMENT", PyReturnStatementImpl::new);
  PyElementType TRY_EXCEPT_STATEMENT = new PyElementType("TRY_EXCEPT_STATEMENT", PyTryExceptStatementImpl::new);
  PyElementType WITH_STATEMENT = new PyElementType("WITH_STATEMENT", PyWithStatementImpl::new);
  PyElementType WHILE_STATEMENT = new PyElementType("WHILE_STATEMENT", PyWhileStatementImpl::new);
  PyElementType STATEMENT_LIST = new PyElementType("STATEMENT_LIST", PyStatementListImpl::new);

  PyElementType NONLOCAL_STATEMENT = new PyElementType("NONLOCAL_STATEMENT", PyNonlocalStatementImpl::new);

  PyElementType WITH_ITEM = new PyElementType("WITH_ITEM", PyWithItemImpl::new);

  // Expressions
  PyElementType EMPTY_EXPRESSION = new PyElementType("EMPTY_EXPRESSION", PyEmptyExpressionImpl::new);
  PyElementType REFERENCE_EXPRESSION = new PyElementType("REFERENCE_EXPRESSION", PyReferenceExpressionImpl::new);

  PyElementType INTEGER_LITERAL_EXPRESSION = new PyElementType("INTEGER_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl::new);
  PyElementType FLOAT_LITERAL_EXPRESSION = new PyElementType("FLOAT_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl::new);
  PyElementType IMAGINARY_LITERAL_EXPRESSION = new PyElementType("IMAGINARY_LITERAL_EXPRESSION", PyNumericLiteralExpressionImpl::new);
  PyElementType STRING_LITERAL_EXPRESSION = new PyElementType("STRING_LITERAL_EXPRESSION", PyStringLiteralExpressionImpl::new);
  PyElementType NONE_LITERAL_EXPRESSION = new PyElementType("NONE_LITERAL_EXPRESSION", PyNoneLiteralExpressionImpl::new);
  PyElementType BOOL_LITERAL_EXPRESSION = new PyElementType("BOOL_LITERAL_EXPRESSION", PyBoolLiteralExpressionImpl::new);
  PyElementType PARENTHESIZED_EXPRESSION = new PyElementType("PARENTHESIZED_EXPRESSION", PyParenthesizedExpressionImpl::new);
  PyElementType SUBSCRIPTION_EXPRESSION = new PyElementType("SUBSCRIPTION_EXPRESSION", PySubscriptionExpressionImpl::new);
  PyElementType SLICE_EXPRESSION = new PyElementType("SLICE_EXPRESSION", PySliceExpressionImpl::new);
  PyElementType SLICE_ITEM = new PyElementType("SLICE_ITEM", PySliceItemImpl::new);
  PyElementType BINARY_EXPRESSION = new PyElementType("BINARY_EXPRESSION", PyBinaryExpressionImpl::new);
  PyElementType PREFIX_EXPRESSION = new PyElementType("PREFIX_EXPRESSION", PyPrefixExpressionImpl::new);
  PyElementType CALL_EXPRESSION = new PyElementType("CALL_EXPRESSION", PyCallExpressionImpl::new);
  PyElementType LIST_LITERAL_EXPRESSION = new PyElementType("LIST_LITERAL_EXPRESSION", PyListLiteralExpressionImpl::new);
  PyElementType TUPLE_EXPRESSION = new PyElementType("TUPLE_EXPRESSION", PyTupleExpressionImpl::new);
  PyElementType KEYWORD_ARGUMENT_EXPRESSION = new PyElementType("KEYWORD_ARGUMENT_EXPRESSION", PyKeywordArgumentImpl::new);
  PyElementType STAR_ARGUMENT_EXPRESSION = new PyElementType("STAR_ARGUMENT_EXPRESSION", PyStarArgumentImpl::new);
  PyElementType LAMBDA_EXPRESSION = new PyElementType("LAMBDA_EXPRESSION", PyLambdaExpressionImpl::new);
  PyElementType LIST_COMP_EXPRESSION = new PyElementType("LIST_COMP_EXPRESSION", PyListCompExpressionImpl::new);
  PyElementType DICT_LITERAL_EXPRESSION = new PyElementType("DICT_LITERAL_EXPRESSION", PyDictLiteralExpressionImpl::new);
  PyElementType KEY_VALUE_EXPRESSION = new PyElementType("KEY_VALUE_EXPRESSION", PyKeyValueExpressionImpl::new);
  PyElementType REPR_EXPRESSION = new PyElementType("REPR_EXPRESSION", PyReprExpressionImpl::new);
  PyElementType GENERATOR_EXPRESSION = new PyElementType("GENERATOR_EXPRESSION", PyGeneratorExpressionImpl::new);
  PyElementType CONDITIONAL_EXPRESSION = new PyElementType("CONDITIONAL_EXPRESSION", PyConditionalExpressionImpl::new);
  PyElementType YIELD_EXPRESSION = new PyElementType("YIELD_EXPRESSION", PyYieldExpressionImpl::new);
  PyElementType STAR_EXPRESSION = new PyElementType("STAR_EXPRESSION", PyStarExpressionImpl::new);
  PyElementType DOUBLE_STAR_EXPRESSION = new PyElementType("DOUBLE_STAR_EXPRESSION", PyDoubleStarExpressionImpl::new);
  PyElementType ASSIGNMENT_EXPRESSION = new PyElementType("ASSIGNMENT_EXPRESSION", PyAssignmentExpressionImpl::new);

  PyElementType SET_LITERAL_EXPRESSION = new PyElementType("SET_LITERAL_EXPRESSION", PySetLiteralExpressionImpl::new);
  PyElementType SET_COMP_EXPRESSION = new PyElementType("SET_COMP_EXPRESSION", PySetCompExpressionImpl::new);
  PyElementType DICT_COMP_EXPRESSION = new PyElementType("DICT_COMP_EXPRESSION", PyDictCompExpressionImpl::new);

  TokenSet STATEMENT_LISTS = TokenSet.create(STATEMENT_LIST);

  TokenSet BINARY_OPS = TokenSet.create(PyTokenTypes.OR_KEYWORD, PyTokenTypes.AND_KEYWORD, PyTokenTypes.LT, PyTokenTypes.GT,
                                        PyTokenTypes.OR, PyTokenTypes.XOR, PyTokenTypes.AND, PyTokenTypes.LTLT, PyTokenTypes.GTGT,
                                        PyTokenTypes.EQEQ, PyTokenTypes.GE, PyTokenTypes.LE, PyTokenTypes.NE, PyTokenTypes.NE_OLD,
                                        PyTokenTypes.IN_KEYWORD, PyTokenTypes.IS_KEYWORD, PyTokenTypes.NOT_KEYWORD, PyTokenTypes.PLUS,
                                        PyTokenTypes.MINUS, PyTokenTypes.MULT, PyTokenTypes.AT, PyTokenTypes.FLOORDIV, PyTokenTypes.DIV,
                                        PyTokenTypes.PERC, PyTokenTypes.EXP);

  TokenSet UNARY_OPS = TokenSet.create(PyTokenTypes.NOT_KEYWORD, PyTokenTypes.PLUS, PyTokenTypes.MINUS, PyTokenTypes.TILDE,
                                       PyTokenTypes.AWAIT_KEYWORD);

  // Parts
  PyElementType IF_PART_IF = new PyElementType("IF_IF", PyIfPartIfImpl::new);
  PyElementType IF_PART_ELIF = new PyElementType("IF_ELIF", PyIfPartElifImpl::new);

  PyElementType FOR_PART = new PyElementType("FOR_PART", PyForPartImpl::new);
  PyElementType WHILE_PART = new PyElementType("WHILE_PART", PyWhilePartImpl::new);

  PyElementType TRY_PART = new PyElementType("TRY_PART", PyTryPartImpl::new);
  PyElementType FINALLY_PART = new PyElementType("FINALLY_PART", PyFinallyPartImpl::new);

  PyElementType ELSE_PART = new PyElementType("ELSE_PART", PyElsePartImpl::new);

  TokenSet PARTS = TokenSet.create(IF_PART_IF, IF_PART_ELIF, FOR_PART, WHILE_PART, TRY_PART, FINALLY_PART, ELSE_PART, EXCEPT_PART);
  TokenSet ELIFS = TokenSet.create(IF_PART_ELIF);
  TokenSet STAR_PARAMETERS = TokenSet.create(NAMED_PARAMETER, STAR_ARGUMENT_EXPRESSION, STAR_EXPRESSION, DOUBLE_STAR_EXPRESSION);
  TokenSet CLASS_OR_FUNCTION = TokenSet.create(CLASS_DECLARATION, FUNCTION_DECLARATION);
  TokenSet IMPORT_STATEMENTS = TokenSet.create(IMPORT_STATEMENT, FROM_IMPORT_STATEMENT);

  PyElementType FSTRING_NODE = new PyElementType("FSTRING_NODE", PyFormattedStringElementImpl::new);
  PyElementType FSTRING_FRAGMENT = new PyElementType("FSTRING_FRAGMENT", PyFStringFragmentImpl::new);
  PyElementType FSTRING_FRAGMENT_FORMAT_PART = new PyElementType("FSTRING_FRAGMENT_FORMAT_PART", PyFStringFragmentFormatPartImpl::new);
}
