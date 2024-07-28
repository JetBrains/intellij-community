// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.psi.PyElementType;

import static com.jetbrains.python.PyElementTypesFacade.Companion;

public interface PyElementTypes {
  IElementType FUNCTION_DECLARATION = Companion.getINSTANCE().getFunctionDeclaration();
  IElementType CLASS_DECLARATION = Companion.getINSTANCE().getClassDeclaration();
  IElementType PARAMETER_LIST = Companion.getINSTANCE().getParameterList();
  IElementType DECORATOR_LIST = Companion.getINSTANCE().getDecoratorList();
  IElementType NAMED_PARAMETER = Companion.getINSTANCE().getNamedParameter();
  IElementType TUPLE_PARAMETER = Companion.getINSTANCE().getTupleParameter();
  IElementType SLASH_PARAMETER = Companion.getINSTANCE().getSlashParameter();
  IElementType SINGLE_STAR_PARAMETER = Companion.getINSTANCE().getSingleStarParameter();
  IElementType DECORATOR_CALL = Companion.getINSTANCE().getDecoratorCall();
  IElementType IMPORT_ELEMENT = Companion.getINSTANCE().getImportElement();
  IElementType ANNOTATION = Companion.getINSTANCE().getAnnotation();
  IElementType STAR_IMPORT_ELEMENT = Companion.getINSTANCE().getStarImportElement();
  IElementType EXCEPT_PART = Companion.getINSTANCE().getExceptPart();
  IElementType FROM_IMPORT_STATEMENT = Companion.getINSTANCE().getFromImportStatement();
  IElementType IMPORT_STATEMENT = Companion.getINSTANCE().getImportStatement();
  IElementType TARGET_EXPRESSION = Companion.getINSTANCE().getTargetExpression();
  IElementType TYPE_PARAMETER = Companion.getINSTANCE().getTypeParameter();
  IElementType TYPE_PARAMETER_LIST = Companion.getINSTANCE().getTypeParameterList();
  IElementType TYPE_ALIAS_STATEMENT = Companion.getINSTANCE().getTypeAliasStatement();
  IElementType STATEMENT_LIST = Companion.getINSTANCE().getStatementList();

  TokenSet PARAMETER_LIST_SET = TokenSet.create(PARAMETER_LIST);

  TokenSet FORMAL_PARAMETER_SET = TokenSet.create(NAMED_PARAMETER);

  PyElementType ARGUMENT_LIST = new PyElementType("ARGUMENT_LIST", Companion.getINSTANCE().getArgumentListConstructor());
  PyElementType PRINT_TARGET = new PyElementType("PRINT_TARGET", Companion.getINSTANCE().getPrintTargetConstructor());
  PyElementType DECORATOR = new PyElementType("DECORATOR", Companion.getINSTANCE().getDecoratorConstructor());

  // Statements
  PyElementType EXPRESSION_STATEMENT = new PyElementType("EXPRESSION_STATEMENT", Companion.getINSTANCE().getExpressionStatementConstructor());
  PyElementType ASSIGNMENT_STATEMENT = new PyElementType("ASSIGNMENT_STATEMENT", Companion.getINSTANCE().getAssignmentStatementConstructor());
  PyElementType AUG_ASSIGNMENT_STATEMENT = new PyElementType("AUG_ASSIGNMENT_STATEMENT", Companion.getINSTANCE().getAugAssignmentStatementConstructor());
  PyElementType ASSERT_STATEMENT = new PyElementType("ASSERT_STATEMENT", Companion.getINSTANCE().getAssertStatementConstructor());
  PyElementType BREAK_STATEMENT = new PyElementType("BREAK_STATEMENT", Companion.getINSTANCE().getBreakStatementConstructor());
  PyElementType CONTINUE_STATEMENT = new PyElementType("CONTINUE_STATEMENT", Companion.getINSTANCE().getContinueStatementConstructor());
  PyElementType DEL_STATEMENT = new PyElementType("DEL_STATEMENT", Companion.getINSTANCE().getDelStatementConstructor());
  PyElementType EXEC_STATEMENT = new PyElementType("EXEC_STATEMENT", Companion.getINSTANCE().getExecStatementConstructor());
  PyElementType FOR_STATEMENT = new PyElementType("FOR_STATEMENT", Companion.getINSTANCE().getForStatementConstructor());
  PyElementType TYPE_DECLARATION_STATEMENT = new PyElementType("TYPE_DECLARATION_STATEMENT", Companion.getINSTANCE().getTypeDeclarationStatementConstructor());

  PyElementType GLOBAL_STATEMENT = new PyElementType("GLOBAL_STATEMENT", Companion.getINSTANCE().getGlobalStatementConstructor());
  PyElementType IF_STATEMENT = new PyElementType("IF_STATEMENT", Companion.getINSTANCE().getIfStatementConstructor());
  PyElementType PASS_STATEMENT = new PyElementType("PASS_STATEMENT", Companion.getINSTANCE().getPassStatementConstructor());
  PyElementType PRINT_STATEMENT = new PyElementType("PRINT_STATEMENT", Companion.getINSTANCE().getPrintStatementConstructor());
  PyElementType RAISE_STATEMENT = new PyElementType("RAISE_STATEMENT", Companion.getINSTANCE().getRaiseStatementConstructor());
  PyElementType RETURN_STATEMENT = new PyElementType("RETURN_STATEMENT", Companion.getINSTANCE().getReturnStatementConstructor());
  PyElementType TRY_EXCEPT_STATEMENT = new PyElementType("TRY_EXCEPT_STATEMENT", Companion.getINSTANCE().getTryExceptStatementConstructor());
  PyElementType WITH_STATEMENT = new PyElementType("WITH_STATEMENT", Companion.getINSTANCE().getWithStatementConstructor());
  PyElementType WHILE_STATEMENT = new PyElementType("WHILE_STATEMENT", Companion.getINSTANCE().getWhileStatementConstructor());

  PyElementType NONLOCAL_STATEMENT = new PyElementType("NONLOCAL_STATEMENT", Companion.getINSTANCE().getNonlocalStatementConstructor());
  PyElementType WITH_ITEM = new PyElementType("WITH_ITEM", Companion.getINSTANCE().getWithItemConstructor());
  // Expressions
  PyElementType EMPTY_EXPRESSION = new PyElementType("EMPTY_EXPRESSION", Companion.getINSTANCE().getEmptyExpressionConstructor());
  PyElementType REFERENCE_EXPRESSION = new PyElementType("REFERENCE_EXPRESSION", Companion.getINSTANCE().getReferenceExpressionConstructor());

  PyElementType INTEGER_LITERAL_EXPRESSION = new PyElementType("INTEGER_LITERAL_EXPRESSION", Companion.getINSTANCE().getIntegerLiteralExpressionConstructor());
  PyElementType FLOAT_LITERAL_EXPRESSION = new PyElementType("FLOAT_LITERAL_EXPRESSION", Companion.getINSTANCE().getFloatLiteralExpressionConstructor());
  PyElementType IMAGINARY_LITERAL_EXPRESSION = new PyElementType("IMAGINARY_LITERAL_EXPRESSION", Companion.getINSTANCE().getImaginaryLiteralExpressionConstructor());
  PyElementType STRING_LITERAL_EXPRESSION = new PyElementType("STRING_LITERAL_EXPRESSION", Companion.getINSTANCE().getStringLiteralExpressionConstructor());
  PyElementType NONE_LITERAL_EXPRESSION = new PyElementType("NONE_LITERAL_EXPRESSION", Companion.getINSTANCE().getNoneLiteralExpressionConstructor());
  PyElementType BOOL_LITERAL_EXPRESSION = new PyElementType("BOOL_LITERAL_EXPRESSION", Companion.getINSTANCE().getBoolLiteralExpressionConstructor());
  PyElementType PARENTHESIZED_EXPRESSION = new PyElementType("PARENTHESIZED_EXPRESSION", Companion.getINSTANCE().getParenthesizedExpressionConstructor());
  PyElementType SUBSCRIPTION_EXPRESSION = new PyElementType("SUBSCRIPTION_EXPRESSION", Companion.getINSTANCE().getSubscriptionExpressionConstructor());
  PyElementType SLICE_EXPRESSION = new PyElementType("SLICE_EXPRESSION", Companion.getINSTANCE().getSliceExpressionConstructor());
  PyElementType SLICE_ITEM = new PyElementType("SLICE_ITEM", Companion.getINSTANCE().getSliceItemConstructor());
  PyElementType BINARY_EXPRESSION = new PyElementType("BINARY_EXPRESSION", Companion.getINSTANCE().getBinaryExpressionConstructor());
  PyElementType PREFIX_EXPRESSION = new PyElementType("PREFIX_EXPRESSION", Companion.getINSTANCE().getPrefixExpressionConstructor());
  PyElementType CALL_EXPRESSION = new PyElementType("CALL_EXPRESSION", Companion.getINSTANCE().getCallExpressionConstructor());
  PyElementType LIST_LITERAL_EXPRESSION = new PyElementType("LIST_LITERAL_EXPRESSION", Companion.getINSTANCE().getListLiteralExpressionConstructor());
  PyElementType TUPLE_EXPRESSION = new PyElementType("TUPLE_EXPRESSION", Companion.getINSTANCE().getTupleExpressionConstructor());
  PyElementType KEYWORD_ARGUMENT_EXPRESSION = new PyElementType("KEYWORD_ARGUMENT_EXPRESSION", Companion.getINSTANCE().getKeywordArgumentExpressionConstructor());
  PyElementType STAR_ARGUMENT_EXPRESSION = new PyElementType("STAR_ARGUMENT_EXPRESSION", Companion.getINSTANCE().getStarArgumentExpressionConstructor());
  PyElementType LAMBDA_EXPRESSION = new PyElementType("LAMBDA_EXPRESSION", Companion.getINSTANCE().getLambdaExpressionConstructor());
  PyElementType LIST_COMP_EXPRESSION = new PyElementType("LIST_COMP_EXPRESSION", Companion.getINSTANCE().getListCompExpressionConstructor());
  PyElementType DICT_LITERAL_EXPRESSION = new PyElementType("DICT_LITERAL_EXPRESSION", Companion.getINSTANCE().getDictLiteralExpressionConstructor());
  PyElementType KEY_VALUE_EXPRESSION = new PyElementType("KEY_VALUE_EXPRESSION", Companion.getINSTANCE().getKeyValueExpressionConstructor());
  PyElementType REPR_EXPRESSION = new PyElementType("REPR_EXPRESSION", Companion.getINSTANCE().getReprExpressionConstructor());
  PyElementType GENERATOR_EXPRESSION = new PyElementType("GENERATOR_EXPRESSION", Companion.getINSTANCE().getGeneratorExpressionConstructor());
  PyElementType CONDITIONAL_EXPRESSION = new PyElementType("CONDITIONAL_EXPRESSION", Companion.getINSTANCE().getConditionalExpressionConstructor());
  PyElementType YIELD_EXPRESSION = new PyElementType("YIELD_EXPRESSION", Companion.getINSTANCE().getYieldExpressionConstructor());
  PyElementType STAR_EXPRESSION = new PyElementType("STAR_EXPRESSION", Companion.getINSTANCE().getStarExpressionConstructor());
  PyElementType DOUBLE_STAR_EXPRESSION = new PyElementType("DOUBLE_STAR_EXPRESSION", Companion.getINSTANCE().getDoubleStarExpressionConstructor());
  PyElementType ASSIGNMENT_EXPRESSION = new PyElementType("ASSIGNMENT_EXPRESSION", Companion.getINSTANCE().getAssignmentExpressionConstructor());

  PyElementType SET_LITERAL_EXPRESSION = new PyElementType("SET_LITERAL_EXPRESSION", Companion.getINSTANCE().getSetLiteralExpressionConstructor());
  PyElementType SET_COMP_EXPRESSION = new PyElementType("SET_COMP_EXPRESSION", Companion.getINSTANCE().getSetCompExpressionConstructor());
  PyElementType DICT_COMP_EXPRESSION = new PyElementType("DICT_COMP_EXPRESSION", Companion.getINSTANCE().getDictCompExpressionConstructor());
  TokenSet STATEMENT_LISTS = TokenSet.create(STATEMENT_LIST);

  // Parts
  PyElementType IF_PART_IF = new PyElementType("IF_IF", Companion.getINSTANCE().getIfPartIfConstructor());
  PyElementType IF_PART_ELIF = new PyElementType("IF_ELIF", Companion.getINSTANCE().getIfPartElifConstructor());

  PyElementType FOR_PART = new PyElementType("FOR_PART", Companion.getINSTANCE().getForPartConstructor());
  PyElementType WHILE_PART = new PyElementType("WHILE_PART", Companion.getINSTANCE().getWhilePartConstructor());

  PyElementType TRY_PART = new PyElementType("TRY_PART", Companion.getINSTANCE().getTryPartConstructor());
  PyElementType FINALLY_PART = new PyElementType("FINALLY_PART", Companion.getINSTANCE().getFinallyPartConstructor());

  PyElementType ELSE_PART = new PyElementType("ELSE_PART", Companion.getINSTANCE().getElsePartConstructor());

  TokenSet PARTS = TokenSet.create(IF_PART_IF, IF_PART_ELIF, FOR_PART, WHILE_PART, TRY_PART, FINALLY_PART, ELSE_PART, EXCEPT_PART);
  TokenSet ELIFS = TokenSet.create(IF_PART_ELIF);
  TokenSet STAR_PARAMETERS = TokenSet.create(NAMED_PARAMETER, STAR_ARGUMENT_EXPRESSION, STAR_EXPRESSION, DOUBLE_STAR_EXPRESSION);
  TokenSet CLASS_OR_FUNCTION = TokenSet.create(CLASS_DECLARATION, FUNCTION_DECLARATION);
  TokenSet IMPORT_STATEMENTS = TokenSet.create(IMPORT_STATEMENT, FROM_IMPORT_STATEMENT);

  PyElementType FSTRING_NODE = new PyElementType("FSTRING_NODE", Companion.getINSTANCE().getFStringNodeConstructor());
  PyElementType FSTRING_FRAGMENT = new PyElementType("FSTRING_FRAGMENT", Companion.getINSTANCE().getFStringFragmentConstructor());
  PyElementType FSTRING_FRAGMENT_FORMAT_PART = new PyElementType("FSTRING_FRAGMENT_FORMAT_PART", Companion.getINSTANCE().getFStringFragmentFormatPartConstructor());

  PyElementType MATCH_STATEMENT = new PyElementType("MATCH_STATEMENT", Companion.getINSTANCE().getMatchStatementConstructor());
  PyElementType CASE_CLAUSE = new PyElementType("CASE_CLAUSE", Companion.getINSTANCE().getCaseClauseConstructor());
  PyElementType LITERAL_PATTERN = new PyElementType("LITERAL_PATTERN", Companion.getINSTANCE().getLiteralPatternConstructor());
  PyElementType VALUE_PATTERN = new PyElementType("VALUE_PATTERN", Companion.getINSTANCE().getValuePatternConstructor());
  PyElementType CAPTURE_PATTERN = new PyElementType("CAPTURE_PATTERN", Companion.getINSTANCE().getCapturePatternConstructor());
  PyElementType WILDCARD_PATTERN = new PyElementType("WILDCARD_PATTERN", Companion.getINSTANCE().getWildcardPatternConstructor());
  PyElementType GROUP_PATTERN = new PyElementType("GROUP_PATTERN", Companion.getINSTANCE().getGroupPatternConstructor());
  PyElementType SEQUENCE_PATTERN = new PyElementType("SEQUENCE_PATTERN", Companion.getINSTANCE().getSequencePatternConstructor());
  PyElementType SINGLE_STAR_PATTERN = new PyElementType("SINGLE_STAR_PATTERN", Companion.getINSTANCE().getSingleStarPatternConstructor());
  PyElementType DOUBLE_STAR_PATTERN = new PyElementType("DOUBLE_STAR_PATTERN", Companion.getINSTANCE().getDoubleStarPatternConstructor());
  PyElementType MAPPING_PATTERN = new PyElementType("KEY_VALUE_PATTERN", Companion.getINSTANCE().getMappingPatternConstructor());
  PyElementType KEY_VALUE_PATTERN = new PyElementType("KEY_VALUE_PATTERN", Companion.getINSTANCE().getKeyValuePatternConstructor());
  PyElementType CLASS_PATTERN = new PyElementType("CLASS_PATTERN", Companion.getINSTANCE().getClassPatternConstructor());
  PyElementType PATTERN_ARGUMENT_LIST = new PyElementType("PATTERN_ARGUMENT_LIST", Companion.getINSTANCE().getPatternArgumentListConstructor());
  PyElementType KEYWORD_PATTERN = new PyElementType("KEYWORD_PATTERN", Companion.getINSTANCE().getKeywordPatternConstructor());
  PyElementType OR_PATTERN = new PyElementType("OR_PATTERN", Companion.getINSTANCE().getOrPatternConstructor());
  PyElementType AS_PATTERN = new PyElementType("AS_PATTERN", Companion.getINSTANCE().getAsPatternConstructor());

}
