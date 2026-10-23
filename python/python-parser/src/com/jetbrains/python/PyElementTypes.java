// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyStatementListElementType;

/**
 * The element types of the Python AST. Each type is one constant for every product mode.
 * The PSI classes behind the types come from {@link PyPsiElementFactory} at call time, and the stubs come from the
 * stub registry extension of {@code intellij.python.psi.impl}.
 */
public interface PyElementTypes {
  IElementType FUNCTION_DECLARATION = PyElementType.viaFactory("FUNCTION_DECLARATION", PyPsiElementFactory::getFunctionDeclarationConstructor);
  IElementType CLASS_DECLARATION = PyElementType.viaFactory("CLASS_DECLARATION", PyPsiElementFactory::getClassDeclarationConstructor);
  IElementType PARAMETER_LIST = PyElementType.viaFactory("PARAMETER_LIST", PyPsiElementFactory::getParameterListConstructor);
  IElementType DECORATOR_LIST = PyElementType.viaFactory("DECORATOR_LIST", PyPsiElementFactory::getDecoratorListConstructor);
  IElementType NAMED_PARAMETER = PyElementType.viaFactory("NAMED_PARAMETER", PyPsiElementFactory::getNamedParameterConstructor);
  IElementType TUPLE_PARAMETER = PyElementType.viaFactory("TUPLE_PARAMETER", PyPsiElementFactory::getTupleParameterConstructor);
  IElementType SLASH_PARAMETER = PyElementType.viaFactory("SLASH_PARAMETER", PyPsiElementFactory::getSlashParameterConstructor);
  IElementType SINGLE_STAR_PARAMETER = PyElementType.viaFactory("SINGLE_STAR_PARAMETER", PyPsiElementFactory::getSingleStarParameterConstructor);
  IElementType DECORATOR_CALL = PyElementType.viaFactory("DECORATOR_CALL", PyPsiElementFactory::getDecoratorCallConstructor);
  IElementType IMPORT_ELEMENT = PyElementType.viaFactory("IMPORT_ELEMENT", PyPsiElementFactory::getImportElementConstructor);
  IElementType ANNOTATION = PyElementType.viaFactory("ANNOTATION", PyPsiElementFactory::getAnnotationConstructor);
  IElementType STAR_IMPORT_ELEMENT = PyElementType.viaFactory("STAR_IMPORT_ELEMENT", PyPsiElementFactory::getStarImportElementConstructor);
  IElementType EXCEPT_PART = PyElementType.viaFactory("EXCEPT_PART", PyPsiElementFactory::getExceptPartConstructor);
  IElementType FROM_IMPORT_STATEMENT = PyElementType.viaFactory("FROM_IMPORT_STATEMENT", PyPsiElementFactory::getFromImportStatementConstructor);
  IElementType IMPORT_STATEMENT = PyElementType.viaFactory("IMPORT_STATEMENT", PyPsiElementFactory::getImportStatementConstructor);
  IElementType TARGET_EXPRESSION = PyElementType.viaFactory("TARGET_EXPRESSION", PyPsiElementFactory::getTargetExpressionConstructor);
  IElementType TYPE_PARAMETER = PyElementType.viaFactory("TYPE_PARAMETER", PyPsiElementFactory::getTypeParameterConstructor);
  IElementType TYPE_PARAMETER_LIST = PyElementType.viaFactory("TYPE_PARAMETER_LIST", PyPsiElementFactory::getTypeParameterListConstructor);
  IElementType TYPE_ALIAS_STATEMENT = PyElementType.viaFactory("TYPE_ALIAS_STATEMENT", PyPsiElementFactory::getTypeAliasStatementConstructor);
  IElementType STATEMENT_LIST = new PyStatementListElementType();

  TokenSet PARAMETER_LIST_SET = TokenSet.create(PARAMETER_LIST);

  TokenSet FORMAL_PARAMETER_SET = TokenSet.create(NAMED_PARAMETER);

  PyElementType ARGUMENT_LIST = PyElementType.viaFactory("ARGUMENT_LIST", PyPsiElementFactory::getArgumentListConstructor);
  PyElementType PRINT_TARGET = PyElementType.viaFactory("PRINT_TARGET", PyPsiElementFactory::getPrintTargetConstructor);
  PyElementType DECORATOR = PyElementType.viaFactory("DECORATOR", PyPsiElementFactory::getDecoratorConstructor);

  // Statements
  PyElementType EXPRESSION_STATEMENT = PyElementType.viaFactory("EXPRESSION_STATEMENT", PyPsiElementFactory::getExpressionStatementConstructor);
  PyElementType ASSIGNMENT_STATEMENT = PyElementType.viaFactory("ASSIGNMENT_STATEMENT", PyPsiElementFactory::getAssignmentStatementConstructor);
  PyElementType AUG_ASSIGNMENT_STATEMENT = PyElementType.viaFactory("AUG_ASSIGNMENT_STATEMENT", PyPsiElementFactory::getAugAssignmentStatementConstructor);
  PyElementType ASSERT_STATEMENT = PyElementType.viaFactory("ASSERT_STATEMENT", PyPsiElementFactory::getAssertStatementConstructor);
  PyElementType BREAK_STATEMENT = PyElementType.viaFactory("BREAK_STATEMENT", PyPsiElementFactory::getBreakStatementConstructor);
  PyElementType CONTINUE_STATEMENT = PyElementType.viaFactory("CONTINUE_STATEMENT", PyPsiElementFactory::getContinueStatementConstructor);
  PyElementType DEL_STATEMENT = PyElementType.viaFactory("DEL_STATEMENT", PyPsiElementFactory::getDelStatementConstructor);
  PyElementType EXEC_STATEMENT = PyElementType.viaFactory("EXEC_STATEMENT", PyPsiElementFactory::getExecStatementConstructor);
  PyElementType FOR_STATEMENT = PyElementType.viaFactory("FOR_STATEMENT", PyPsiElementFactory::getForStatementConstructor);
  PyElementType TYPE_DECLARATION_STATEMENT = PyElementType.viaFactory("TYPE_DECLARATION_STATEMENT", PyPsiElementFactory::getTypeDeclarationStatementConstructor);

  PyElementType GLOBAL_STATEMENT = PyElementType.viaFactory("GLOBAL_STATEMENT", PyPsiElementFactory::getGlobalStatementConstructor);
  PyElementType IF_STATEMENT = PyElementType.viaFactory("IF_STATEMENT", PyPsiElementFactory::getIfStatementConstructor);
  PyElementType PASS_STATEMENT = PyElementType.viaFactory("PASS_STATEMENT", PyPsiElementFactory::getPassStatementConstructor);
  PyElementType PRINT_STATEMENT = PyElementType.viaFactory("PRINT_STATEMENT", PyPsiElementFactory::getPrintStatementConstructor);
  PyElementType RAISE_STATEMENT = PyElementType.viaFactory("RAISE_STATEMENT", PyPsiElementFactory::getRaiseStatementConstructor);
  PyElementType RETURN_STATEMENT = PyElementType.viaFactory("RETURN_STATEMENT", PyPsiElementFactory::getReturnStatementConstructor);
  PyElementType TRY_EXCEPT_STATEMENT = PyElementType.viaFactory("TRY_EXCEPT_STATEMENT", PyPsiElementFactory::getTryExceptStatementConstructor);
  PyElementType WITH_STATEMENT = PyElementType.viaFactory("WITH_STATEMENT", PyPsiElementFactory::getWithStatementConstructor);
  PyElementType WHILE_STATEMENT = PyElementType.viaFactory("WHILE_STATEMENT", PyPsiElementFactory::getWhileStatementConstructor);

  PyElementType NONLOCAL_STATEMENT = PyElementType.viaFactory("NONLOCAL_STATEMENT", PyPsiElementFactory::getNonlocalStatementConstructor);
  PyElementType WITH_ITEM = PyElementType.viaFactory("WITH_ITEM", PyPsiElementFactory::getWithItemConstructor);
  // Expressions
  PyElementType EMPTY_EXPRESSION = PyElementType.viaFactory("EMPTY_EXPRESSION", PyPsiElementFactory::getEmptyExpressionConstructor);
  PyElementType REFERENCE_EXPRESSION = PyElementType.viaFactory("REFERENCE_EXPRESSION", PyPsiElementFactory::getReferenceExpressionConstructor);

  PyElementType INTEGER_LITERAL_EXPRESSION = PyElementType.viaFactory("INTEGER_LITERAL_EXPRESSION", PyPsiElementFactory::getIntegerLiteralExpressionConstructor);
  PyElementType FLOAT_LITERAL_EXPRESSION = PyElementType.viaFactory("FLOAT_LITERAL_EXPRESSION", PyPsiElementFactory::getFloatLiteralExpressionConstructor);
  PyElementType IMAGINARY_LITERAL_EXPRESSION = PyElementType.viaFactory("IMAGINARY_LITERAL_EXPRESSION", PyPsiElementFactory::getImaginaryLiteralExpressionConstructor);
  PyElementType STRING_LITERAL_EXPRESSION = PyElementType.viaFactory("STRING_LITERAL_EXPRESSION", PyPsiElementFactory::getStringLiteralExpressionConstructor);
  PyElementType NONE_LITERAL_EXPRESSION = PyElementType.viaFactory("NONE_LITERAL_EXPRESSION", PyPsiElementFactory::getNoneLiteralExpressionConstructor);
  PyElementType ELLIPSIS_LITERAL_EXPRESSION = PyElementType.viaFactory("ELLIPSIS_LITERAL_EXPRESSION", PyPsiElementFactory::getEllipsisLiteralExpressionConstructor);
  PyElementType BOOL_LITERAL_EXPRESSION = PyElementType.viaFactory("BOOL_LITERAL_EXPRESSION", PyPsiElementFactory::getBoolLiteralExpressionConstructor);
  PyElementType PARENTHESIZED_EXPRESSION = PyElementType.viaFactory("PARENTHESIZED_EXPRESSION", PyPsiElementFactory::getParenthesizedExpressionConstructor);
  PyElementType SUBSCRIPTION_EXPRESSION = PyElementType.viaFactory("SUBSCRIPTION_EXPRESSION", PyPsiElementFactory::getSubscriptionExpressionConstructor);
  PyElementType SLICE_ITEM = PyElementType.viaFactory("SLICE_ITEM", PyPsiElementFactory::getSliceItemConstructor);
  PyElementType BINARY_EXPRESSION = PyElementType.viaFactory("BINARY_EXPRESSION", PyPsiElementFactory::getBinaryExpressionConstructor);
  PyElementType PREFIX_EXPRESSION = PyElementType.viaFactory("PREFIX_EXPRESSION", PyPsiElementFactory::getPrefixExpressionConstructor);
  PyElementType CALL_EXPRESSION = PyElementType.viaFactory("CALL_EXPRESSION", PyPsiElementFactory::getCallExpressionConstructor);
  PyElementType LIST_LITERAL_EXPRESSION = PyElementType.viaFactory("LIST_LITERAL_EXPRESSION", PyPsiElementFactory::getListLiteralExpressionConstructor);
  PyElementType TUPLE_EXPRESSION = PyElementType.viaFactory("TUPLE_EXPRESSION", PyPsiElementFactory::getTupleExpressionConstructor);
  PyElementType KEYWORD_ARGUMENT_EXPRESSION = PyElementType.viaFactory("KEYWORD_ARGUMENT_EXPRESSION", PyPsiElementFactory::getKeywordArgumentExpressionConstructor);
  PyElementType STAR_ARGUMENT_EXPRESSION = PyElementType.viaFactory("STAR_ARGUMENT_EXPRESSION", PyPsiElementFactory::getStarArgumentExpressionConstructor);
  PyElementType LAMBDA_EXPRESSION = PyElementType.viaFactory("LAMBDA_EXPRESSION", PyPsiElementFactory::getLambdaExpressionConstructor);
  PyElementType LIST_COMP_EXPRESSION = PyElementType.viaFactory("LIST_COMP_EXPRESSION", PyPsiElementFactory::getListCompExpressionConstructor);
  PyElementType DICT_LITERAL_EXPRESSION = PyElementType.viaFactory("DICT_LITERAL_EXPRESSION", PyPsiElementFactory::getDictLiteralExpressionConstructor);
  PyElementType KEY_VALUE_EXPRESSION = PyElementType.viaFactory("KEY_VALUE_EXPRESSION", PyPsiElementFactory::getKeyValueExpressionConstructor);
  PyElementType REPR_EXPRESSION = PyElementType.viaFactory("REPR_EXPRESSION", PyPsiElementFactory::getReprExpressionConstructor);
  PyElementType GENERATOR_EXPRESSION = PyElementType.viaFactory("GENERATOR_EXPRESSION", PyPsiElementFactory::getGeneratorExpressionConstructor);
  PyElementType CONDITIONAL_EXPRESSION = PyElementType.viaFactory("CONDITIONAL_EXPRESSION", PyPsiElementFactory::getConditionalExpressionConstructor);
  PyElementType YIELD_EXPRESSION = PyElementType.viaFactory("YIELD_EXPRESSION", PyPsiElementFactory::getYieldExpressionConstructor);
  PyElementType STAR_EXPRESSION = PyElementType.viaFactory("STAR_EXPRESSION", PyPsiElementFactory::getStarExpressionConstructor);
  PyElementType DOUBLE_STAR_EXPRESSION = PyElementType.viaFactory("DOUBLE_STAR_EXPRESSION", PyPsiElementFactory::getDoubleStarExpressionConstructor);
  PyElementType ASSIGNMENT_EXPRESSION = PyElementType.viaFactory("ASSIGNMENT_EXPRESSION", PyPsiElementFactory::getAssignmentExpressionConstructor);

  PyElementType SET_LITERAL_EXPRESSION = PyElementType.viaFactory("SET_LITERAL_EXPRESSION", PyPsiElementFactory::getSetLiteralExpressionConstructor);
  PyElementType SET_COMP_EXPRESSION = PyElementType.viaFactory("SET_COMP_EXPRESSION", PyPsiElementFactory::getSetCompExpressionConstructor);
  PyElementType DICT_COMP_EXPRESSION = PyElementType.viaFactory("DICT_COMP_EXPRESSION", PyPsiElementFactory::getDictCompExpressionConstructor);
  TokenSet STATEMENT_LISTS = TokenSet.create(STATEMENT_LIST);

  // Parts
  PyElementType IF_PART_IF = PyElementType.viaFactory("IF_IF", PyPsiElementFactory::getIfPartIfConstructor);
  PyElementType IF_PART_ELIF = PyElementType.viaFactory("IF_ELIF", PyPsiElementFactory::getIfPartElifConstructor);

  PyElementType FOR_PART = PyElementType.viaFactory("FOR_PART", PyPsiElementFactory::getForPartConstructor);
  PyElementType WHILE_PART = PyElementType.viaFactory("WHILE_PART", PyPsiElementFactory::getWhilePartConstructor);

  PyElementType TRY_PART = PyElementType.viaFactory("TRY_PART", PyPsiElementFactory::getTryPartConstructor);
  PyElementType FINALLY_PART = PyElementType.viaFactory("FINALLY_PART", PyPsiElementFactory::getFinallyPartConstructor);

  PyElementType ELSE_PART = PyElementType.viaFactory("ELSE_PART", PyPsiElementFactory::getElsePartConstructor);

  TokenSet PARTS = TokenSet.create(IF_PART_IF, IF_PART_ELIF, FOR_PART, WHILE_PART, TRY_PART, FINALLY_PART, ELSE_PART, EXCEPT_PART);
  TokenSet ELIFS = TokenSet.create(IF_PART_ELIF);
  TokenSet STAR_PARAMETERS = TokenSet.create(NAMED_PARAMETER, STAR_ARGUMENT_EXPRESSION, STAR_EXPRESSION, DOUBLE_STAR_EXPRESSION);
  TokenSet CLASS_OR_FUNCTION = TokenSet.create(CLASS_DECLARATION, FUNCTION_DECLARATION);
  TokenSet IMPORT_STATEMENTS = TokenSet.create(IMPORT_STATEMENT, FROM_IMPORT_STATEMENT);
  TokenSet DECORATORS = TokenSet.create(DECORATOR_CALL);
  TokenSet IMPORT_ELEMENTS = TokenSet.create(IMPORT_ELEMENT);
  TokenSet TYPE_PARAMETERS = TokenSet.create(TYPE_PARAMETER);
  TokenSet KEY_VALUE_EXPRESSIONS = TokenSet.create(KEY_VALUE_EXPRESSION);
  TokenSet TARGET_EXPRESSIONS = TokenSet.create(TARGET_EXPRESSION);
  TokenSet EXCEPT_PARTS = TokenSet.create(EXCEPT_PART);

  PyElementType FSTRING_NODE = PyElementType.viaFactory("FSTRING_NODE", PyPsiElementFactory::getFStringNodeConstructor);
  PyElementType FSTRING_FRAGMENT = PyElementType.viaFactory("FSTRING_FRAGMENT", PyPsiElementFactory::getFStringFragmentConstructor);
  PyElementType FSTRING_FRAGMENT_FORMAT_PART = PyElementType.viaFactory("FSTRING_FRAGMENT_FORMAT_PART", PyPsiElementFactory::getFStringFragmentFormatPartConstructor);

  PyElementType MATCH_STATEMENT = PyElementType.viaFactory("MATCH_STATEMENT", PyPsiElementFactory::getMatchStatementConstructor);
  PyElementType CASE_CLAUSE = PyElementType.viaFactory("CASE_CLAUSE", PyPsiElementFactory::getCaseClauseConstructor);
  PyElementType LITERAL_PATTERN = PyElementType.viaFactory("LITERAL_PATTERN", PyPsiElementFactory::getLiteralPatternConstructor);
  PyElementType VALUE_PATTERN = PyElementType.viaFactory("VALUE_PATTERN", PyPsiElementFactory::getValuePatternConstructor);
  PyElementType CAPTURE_PATTERN = PyElementType.viaFactory("CAPTURE_PATTERN", PyPsiElementFactory::getCapturePatternConstructor);
  PyElementType WILDCARD_PATTERN = PyElementType.viaFactory("WILDCARD_PATTERN", PyPsiElementFactory::getWildcardPatternConstructor);
  PyElementType GROUP_PATTERN = PyElementType.viaFactory("GROUP_PATTERN", PyPsiElementFactory::getGroupPatternConstructor);
  PyElementType SEQUENCE_PATTERN = PyElementType.viaFactory("SEQUENCE_PATTERN", PyPsiElementFactory::getSequencePatternConstructor);
  PyElementType SINGLE_STAR_PATTERN = PyElementType.viaFactory("SINGLE_STAR_PATTERN", PyPsiElementFactory::getSingleStarPatternConstructor);
  PyElementType DOUBLE_STAR_PATTERN = PyElementType.viaFactory("DOUBLE_STAR_PATTERN", PyPsiElementFactory::getDoubleStarPatternConstructor);
  PyElementType MAPPING_PATTERN = PyElementType.viaFactory("MAPPING_PATTERN", PyPsiElementFactory::getMappingPatternConstructor);
  PyElementType KEY_VALUE_PATTERN = PyElementType.viaFactory("KEY_VALUE_PATTERN", PyPsiElementFactory::getKeyValuePatternConstructor);
  PyElementType CLASS_PATTERN = PyElementType.viaFactory("CLASS_PATTERN", PyPsiElementFactory::getClassPatternConstructor);
  PyElementType PATTERN_ARGUMENT_LIST = PyElementType.viaFactory("PATTERN_ARGUMENT_LIST", PyPsiElementFactory::getPatternArgumentListConstructor);
  PyElementType KEYWORD_PATTERN = PyElementType.viaFactory("KEYWORD_PATTERN", PyPsiElementFactory::getKeywordPatternConstructor);
  PyElementType OR_PATTERN = PyElementType.viaFactory("OR_PATTERN", PyPsiElementFactory::getOrPatternConstructor);
  PyElementType AS_PATTERN = PyElementType.viaFactory("AS_PATTERN", PyPsiElementFactory::getAsPatternConstructor);

}
