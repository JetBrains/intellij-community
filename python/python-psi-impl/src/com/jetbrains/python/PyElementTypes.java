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

  PyElementType ARGUMENT_LIST = new PyElementType("ARGUMENT_LIST", node -> new PyArgumentListImpl(node));

  PyElementType PRINT_TARGET = new PyElementType("PRINT_TARGET", node -> new PyPrintTargetImpl(node));
  PyElementType DECORATOR = new PyElementType("DECORATOR", node -> new PyDecoratorImpl(node));

  // Statements
  PyElementType EXPRESSION_STATEMENT = new PyElementType("EXPRESSION_STATEMENT", node -> new PyExpressionStatementImpl(node));
  PyElementType ASSIGNMENT_STATEMENT = new PyElementType("ASSIGNMENT_STATEMENT", node -> new PyAssignmentStatementImpl(node));
  PyElementType AUG_ASSIGNMENT_STATEMENT = new PyElementType("AUG_ASSIGNMENT_STATEMENT", node -> new PyAugAssignmentStatementImpl(node));
  PyElementType ASSERT_STATEMENT = new PyElementType("ASSERT_STATEMENT", node -> new PyAssertStatementImpl(node));
  PyElementType BREAK_STATEMENT = new PyElementType("BREAK_STATEMENT", node -> new PyBreakStatementImpl(node));
  PyElementType CONTINUE_STATEMENT = new PyElementType("CONTINUE_STATEMENT", node -> new PyContinueStatementImpl(node));
  PyElementType DEL_STATEMENT = new PyElementType("DEL_STATEMENT", node -> new PyDelStatementImpl(node));
  PyElementType EXEC_STATEMENT = new PyElementType("EXEC_STATEMENT", node -> new PyExecStatementImpl(node));
  PyElementType FOR_STATEMENT = new PyElementType("FOR_STATEMENT", node -> new PyForStatementImpl(node));
  PyElementType TYPE_DECLARATION_STATEMENT = new PyElementType("TYPE_DECLARATION_STATEMENT", node -> new PyTypeDeclarationStatementImpl(node));

  PyElementType GLOBAL_STATEMENT = new PyElementType("GLOBAL_STATEMENT", node -> new PyGlobalStatementImpl(node));
  PyElementType IF_STATEMENT = new PyElementType("IF_STATEMENT", node -> new PyIfStatementImpl(node));
  PyElementType PASS_STATEMENT = new PyElementType("PASS_STATEMENT", node -> new PyPassStatementImpl(node));
  PyElementType PRINT_STATEMENT = new PyElementType("PRINT_STATEMENT", node -> new PyPrintStatementImpl(node));
  PyElementType RAISE_STATEMENT = new PyElementType("RAISE_STATEMENT", node -> new PyRaiseStatementImpl(node));
  PyElementType RETURN_STATEMENT = new PyElementType("RETURN_STATEMENT", node -> new PyReturnStatementImpl(node));
  PyElementType TRY_EXCEPT_STATEMENT = new PyElementType("TRY_EXCEPT_STATEMENT", node -> new PyTryExceptStatementImpl(node));
  PyElementType WITH_STATEMENT = new PyElementType("WITH_STATEMENT", node -> new PyWithStatementImpl(node));
  PyElementType WHILE_STATEMENT = new PyElementType("WHILE_STATEMENT", node -> new PyWhileStatementImpl(node));
  PyElementType STATEMENT_LIST = new PyElementType("STATEMENT_LIST", node -> new PyStatementListImpl(node));

  PyElementType NONLOCAL_STATEMENT = new PyElementType("NONLOCAL_STATEMENT", node -> new PyNonlocalStatementImpl(node));

  PyElementType WITH_ITEM = new PyElementType("WITH_ITEM", node -> new PyWithItemImpl(node));

  // Expressions
  PyElementType EMPTY_EXPRESSION = new PyElementType("EMPTY_EXPRESSION", node -> new PyEmptyExpressionImpl(node));
  PyElementType REFERENCE_EXPRESSION = new PyElementType("REFERENCE_EXPRESSION", node -> new PyReferenceExpressionImpl(node));

  PyElementType INTEGER_LITERAL_EXPRESSION = new PyElementType("INTEGER_LITERAL_EXPRESSION", node -> new PyNumericLiteralExpressionImpl(node));
  PyElementType FLOAT_LITERAL_EXPRESSION = new PyElementType("FLOAT_LITERAL_EXPRESSION", node -> new PyNumericLiteralExpressionImpl(node));
  PyElementType IMAGINARY_LITERAL_EXPRESSION = new PyElementType("IMAGINARY_LITERAL_EXPRESSION", node -> new PyNumericLiteralExpressionImpl(node));
  PyElementType STRING_LITERAL_EXPRESSION = new PyElementType("STRING_LITERAL_EXPRESSION", node -> new PyStringLiteralExpressionImpl(node));
  PyElementType NONE_LITERAL_EXPRESSION = new PyElementType("NONE_LITERAL_EXPRESSION", node -> new PyNoneLiteralExpressionImpl(node));
  PyElementType BOOL_LITERAL_EXPRESSION = new PyElementType("BOOL_LITERAL_EXPRESSION", node -> new PyBoolLiteralExpressionImpl(node));
  PyElementType PARENTHESIZED_EXPRESSION = new PyElementType("PARENTHESIZED_EXPRESSION", node -> new PyParenthesizedExpressionImpl(node));
  PyElementType SUBSCRIPTION_EXPRESSION = new PyElementType("SUBSCRIPTION_EXPRESSION", node -> new PySubscriptionExpressionImpl(node));
  PyElementType SLICE_EXPRESSION = new PyElementType("SLICE_EXPRESSION", node -> new PySliceExpressionImpl(node));
  PyElementType SLICE_ITEM = new PyElementType("SLICE_ITEM", node -> new PySliceItemImpl(node));
  PyElementType BINARY_EXPRESSION = new PyElementType("BINARY_EXPRESSION", node -> new PyBinaryExpressionImpl(node));
  PyElementType PREFIX_EXPRESSION = new PyElementType("PREFIX_EXPRESSION", node -> new PyPrefixExpressionImpl(node));
  PyElementType CALL_EXPRESSION = new PyElementType("CALL_EXPRESSION", node -> new PyCallExpressionImpl(node));
  PyElementType LIST_LITERAL_EXPRESSION = new PyElementType("LIST_LITERAL_EXPRESSION", node -> new PyListLiteralExpressionImpl(node));
  PyElementType TUPLE_EXPRESSION = new PyElementType("TUPLE_EXPRESSION", node -> new PyTupleExpressionImpl(node));
  PyElementType KEYWORD_ARGUMENT_EXPRESSION = new PyElementType("KEYWORD_ARGUMENT_EXPRESSION", node -> new PyKeywordArgumentImpl(node));
  PyElementType STAR_ARGUMENT_EXPRESSION = new PyElementType("STAR_ARGUMENT_EXPRESSION", node -> new PyStarArgumentImpl(node));
  PyElementType LAMBDA_EXPRESSION = new PyElementType("LAMBDA_EXPRESSION", node -> new PyLambdaExpressionImpl(node));
  PyElementType LIST_COMP_EXPRESSION = new PyElementType("LIST_COMP_EXPRESSION", node -> new PyListCompExpressionImpl(node));
  PyElementType DICT_LITERAL_EXPRESSION = new PyElementType("DICT_LITERAL_EXPRESSION", node -> new PyDictLiteralExpressionImpl(node));
  PyElementType KEY_VALUE_EXPRESSION = new PyElementType("KEY_VALUE_EXPRESSION", node -> new PyKeyValueExpressionImpl(node));
  PyElementType REPR_EXPRESSION = new PyElementType("REPR_EXPRESSION", node -> new PyReprExpressionImpl(node));
  PyElementType GENERATOR_EXPRESSION = new PyElementType("GENERATOR_EXPRESSION", node -> new PyGeneratorExpressionImpl(node));
  PyElementType CONDITIONAL_EXPRESSION = new PyElementType("CONDITIONAL_EXPRESSION", node -> new PyConditionalExpressionImpl(node));
  PyElementType YIELD_EXPRESSION = new PyElementType("YIELD_EXPRESSION", node -> new PyYieldExpressionImpl(node));
  PyElementType STAR_EXPRESSION = new PyElementType("STAR_EXPRESSION", node -> new PyStarExpressionImpl(node));
  PyElementType DOUBLE_STAR_EXPRESSION = new PyElementType("DOUBLE_STAR_EXPRESSION", node -> new PyDoubleStarExpressionImpl(node));
  PyElementType ASSIGNMENT_EXPRESSION = new PyElementType("ASSIGNMENT_EXPRESSION", node -> new PyAssignmentExpressionImpl(node));

  PyElementType SET_LITERAL_EXPRESSION = new PyElementType("SET_LITERAL_EXPRESSION", node -> new PySetLiteralExpressionImpl(node));
  PyElementType SET_COMP_EXPRESSION = new PyElementType("SET_COMP_EXPRESSION", node -> new PySetCompExpressionImpl(node));
  PyElementType DICT_COMP_EXPRESSION = new PyElementType("DICT_COMP_EXPRESSION", node -> new PyDictCompExpressionImpl(node));

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
  PyElementType IF_PART_IF = new PyElementType("IF_IF", node -> new PyIfPartIfImpl(node));
  PyElementType IF_PART_ELIF = new PyElementType("IF_ELIF", node -> new PyIfPartElifImpl(node));

  PyElementType FOR_PART = new PyElementType("FOR_PART", node -> new PyForPartImpl(node));
  PyElementType WHILE_PART = new PyElementType("WHILE_PART", node -> new PyWhilePartImpl(node));

  PyElementType TRY_PART = new PyElementType("TRY_PART", node -> new PyTryPartImpl(node));
  PyElementType FINALLY_PART = new PyElementType("FINALLY_PART", node -> new PyFinallyPartImpl(node));

  PyElementType ELSE_PART = new PyElementType("ELSE_PART", node -> new PyElsePartImpl(node));

  TokenSet PARTS = TokenSet.create(IF_PART_IF, IF_PART_ELIF, FOR_PART, WHILE_PART, TRY_PART, FINALLY_PART, ELSE_PART, EXCEPT_PART);
  TokenSet ELIFS = TokenSet.create(IF_PART_ELIF);
  TokenSet STAR_PARAMETERS = TokenSet.create(NAMED_PARAMETER, STAR_ARGUMENT_EXPRESSION, STAR_EXPRESSION, DOUBLE_STAR_EXPRESSION);
  TokenSet CLASS_OR_FUNCTION = TokenSet.create(CLASS_DECLARATION, FUNCTION_DECLARATION);
  TokenSet IMPORT_STATEMENTS = TokenSet.create(IMPORT_STATEMENT, FROM_IMPORT_STATEMENT);

  PyElementType FSTRING_NODE = new PyElementType("FSTRING_NODE", node -> new PyFormattedStringElementImpl(node));
  PyElementType FSTRING_FRAGMENT = new PyElementType("FSTRING_FRAGMENT", node -> new PyFStringFragmentImpl(node));
  PyElementType FSTRING_FRAGMENT_FORMAT_PART = new PyElementType("FSTRING_FRAGMENT_FORMAT_PART", node -> new PyFStringFragmentFormatPartImpl(node));
}
