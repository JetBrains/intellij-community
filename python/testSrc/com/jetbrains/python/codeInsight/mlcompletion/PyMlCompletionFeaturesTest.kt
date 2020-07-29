// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.mlcompletion

import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PyFunctionImpl
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl

class PyMlCompletionFeaturesTest: PyMlCompletionTestCase() {
  override fun getTestDataPath(): String = super.getTestDataPath() + "/codeInsight/mlcompletion"

  override fun setUp() {
    super.setUp()
    setLanguageLevel(LanguageLevel.PYTHON35)
  }

  // Context features

  fun testIsInConditionSimpleIf() = doContextFeaturesTest(Pair("is_in_condition", MLFeatureValue.binary(true)))
  fun testIsInConditionSimpleElif() = doContextFeaturesTest(Pair("is_in_condition", MLFeatureValue.binary(true)))
  fun testIsInConditionIfBody() = doContextFeaturesTest(Pair("is_in_condition", MLFeatureValue.binary(false)))
  fun testIsInConditionIfBodyNonZeroPrefix() = doContextFeaturesTest(Pair("is_in_condition", MLFeatureValue.binary(false)))
  fun testIsInConditionArgumentContextOfCall() = doContextFeaturesTest(Pair("is_in_condition", MLFeatureValue.binary(false)))
  fun testIsInConditionWhileConditionStatement() = doContextFeaturesTest(Pair("is_in_condition", MLFeatureValue.binary(true)))

  fun testIsInForSimple() = doContextFeaturesTest(Pair("is_in_for_statement", MLFeatureValue.binary(true)))
  fun testIsInForOneLetterPrefix() = doContextFeaturesTest(Pair("is_in_for_statement", MLFeatureValue.binary(true)))
  fun testIsInForAfterIn() = doContextFeaturesTest(Pair("is_in_for_statement", MLFeatureValue.binary(true)))
  fun testIsInForAfterInOneLetterPrefix() = doContextFeaturesTest(Pair("is_in_for_statement", MLFeatureValue.binary(true)))
  fun testIsInForBody() = doContextFeaturesTest(Pair("is_in_for_statement", MLFeatureValue.binary(false)))

  fun testIsAfterIfWithoutElseSimple() = doContextFeaturesTest(Pair("is_after_if_statement_without_else_branch", MLFeatureValue.binary(true)))
  fun testIsAfterIfWithoutElseAfterElse() = doContextFeaturesTest(Pair("is_after_if_statement_without_else_branch", MLFeatureValue.binary(false)))
  fun testIsAfterIfWithoutElseAfterSameLevelLine() = doContextFeaturesTest(Pair("is_after_if_statement_without_else_branch", MLFeatureValue.binary(false)))
  fun testIsAfterIfWithoutElseAfterElifOneLetterPrefix() = doContextFeaturesTest(Pair("is_after_if_statement_without_else_branch", MLFeatureValue.binary(true)))
  fun testIsAfterIfWithoutElseNestedIfAfterElse() = doContextFeaturesTest(Pair("is_after_if_statement_without_else_branch", MLFeatureValue.binary(false)))
  fun testIsAfterIfWithoutElseNestedIfOneLetterPrefix() = doContextFeaturesTest(Pair("is_after_if_statement_without_else_branch", MLFeatureValue.binary(true)))

  fun testIsDirectlyInArgumentsContextSimple() = doContextFeaturesTest(Pair("is_directly_in_arguments_context", MLFeatureValue.binary(true)))
  fun testIsDirectlyInArgumentsContextSecondArgumentWithPrefix() = doContextFeaturesTest(Pair("is_directly_in_arguments_context", MLFeatureValue.binary(true)))
  fun testIsDirectlyInArgumentsContextAfterNamedParameter() = doContextFeaturesTest(Pair("is_directly_in_arguments_context", MLFeatureValue.binary(false)))
  fun testIsDirectlyInArgumentsContextInNestedCall() = doContextFeaturesTest(Pair("is_directly_in_arguments_context", MLFeatureValue.binary(true)))

  fun testArgumentFeaturesFirstArg() = doContextFeaturesTest(Pair("is_in_arguments", MLFeatureValue.binary(true)),
                                                             Pair("is_directly_in_arguments_context", MLFeatureValue.binary(true)),
                                                             Pair("is_into_keyword_arg", MLFeatureValue.binary(false)),
                                                             Pair("have_named_arg_left", MLFeatureValue.binary(false)),
                                                             Pair("have_named_arg_right", MLFeatureValue.binary(false)),
                                                             Pair("argument_index", MLFeatureValue.numerical(0)),
                                                             Pair("number_of_arguments_already", MLFeatureValue.numerical(1)))

  fun testArgumentFeaturesThirdArg() = doContextFeaturesTest(Pair("is_in_arguments", MLFeatureValue.binary(true)),
                                                             Pair("is_directly_in_arguments_context", MLFeatureValue.binary(true)),
                                                             Pair("is_into_keyword_arg", MLFeatureValue.binary(false)),
                                                             Pair("have_named_arg_left", MLFeatureValue.binary(true)),
                                                             Pair("have_named_arg_right", MLFeatureValue.binary(true)),
                                                             Pair("argument_index", MLFeatureValue.numerical(2)),
                                                             Pair("number_of_arguments_already", MLFeatureValue.numerical(4)))

  fun testArgumentFeaturesInNamedArg() = doContextFeaturesTest(Pair("is_in_arguments", MLFeatureValue.binary(true)),
                                                             Pair("is_directly_in_arguments_context", MLFeatureValue.binary(false)),
                                                             Pair("is_into_keyword_arg", MLFeatureValue.binary(true)),
                                                             Pair("have_named_arg_left", MLFeatureValue.binary(true)),
                                                             Pair("have_named_arg_right", MLFeatureValue.binary(true)),
                                                             Pair("argument_index", MLFeatureValue.numerical(2)),
                                                             Pair("number_of_arguments_already", MLFeatureValue.numerical(4)))

  fun testPrevNeighbourKeywordsIfSomethingIn() = doContextFeaturesTest(arrayListOf(Pair("prev_neighbour_keyword_1", MLFeatureValue.numerical(kwId("in")))),
                                                                       arrayListOf("prev_neighbour_keyword_2"))
  fun testPrevNeighbourKeywordsNotIn() = doContextFeaturesTest(Pair("prev_neighbour_keyword_1", MLFeatureValue.numerical(kwId("in"))),
                                                               Pair("prev_neighbour_keyword_2", MLFeatureValue.numerical(kwId("not"))))

  fun testSameLineKeywordsIfSomethingIn() = doContextFeaturesTest(Pair("prev_same_line_keyword_1", MLFeatureValue.numerical(kwId("in"))),
                                                                  Pair("prev_same_line_keyword_2", MLFeatureValue.numerical(kwId("if"))))
  fun testSameLineKeywordsIfSomethingInWithPrevLine() = doContextFeaturesTest(Pair("prev_same_line_keyword_1", MLFeatureValue.numerical(kwId("in"))),
                                                                              Pair("prev_same_line_keyword_2", MLFeatureValue.numerical(kwId("if"))))

  fun testSameColumnKeywordsIfElif() = doContextFeaturesTest(Pair("prev_same_column_keyword_1", MLFeatureValue.numerical(kwId("elif"))),
                                                             Pair("prev_same_column_keyword_2", MLFeatureValue.numerical(kwId("if"))))
  fun testSameColumnKeywordsIfSeparateLineIf() = doContextFeaturesTest(arrayListOf(Pair("prev_same_column_keyword_1", MLFeatureValue.numerical(kwId("if")))),
                                                                       arrayListOf("prev_same_column_keyword_2"))
  fun testSameColumnKeywordsDefDef() = doContextFeaturesTest(Pair("prev_same_column_keyword_1", MLFeatureValue.numerical(kwId("def"))),
                                                             Pair("prev_same_column_keyword_2", MLFeatureValue.numerical(kwId("def"))))
  fun testSameColumnKeywordsDefDefIntoCalss() = doContextFeaturesTest(Pair("prev_same_column_keyword_1", MLFeatureValue.numerical(kwId("def"))),
                                                                      Pair("prev_same_column_keyword_2", MLFeatureValue.numerical(kwId("def"))))
  fun testSameColumnKeywordsIfFor() = doContextFeaturesTest(Pair("prev_same_column_keyword_1", MLFeatureValue.numerical(kwId("if"))),
                                                            Pair("prev_same_column_keyword_2", MLFeatureValue.numerical(kwId("for"))))
  fun testSameColumnKeywordsForSeparateLineIf() = doContextFeaturesTest(arrayListOf(Pair("prev_same_column_keyword_1", MLFeatureValue.numerical(kwId("if")))),
                                                                        arrayListOf("prev_same_column_keyword_2"))

  fun testHaveOpeningRoundBracket() = doContextFeaturesTest(Pair("have_opening_round_bracket", MLFeatureValue.binary(true)))

  fun testHaveOpeningSquareBracket() = doContextFeaturesTest(
    Pair("have_opening_square_bracket", MLFeatureValue.binary(true)),
    Pair("have_opening_round_bracket", MLFeatureValue.binary(false)))

  fun testHaveOpeningBrace() = doContextFeaturesTest(Pair("have_opening_brace", MLFeatureValue.binary(true)))

  fun testInsideClassConstructorPlace() = doContextFeaturesTest(Pair("containing_class_have_constructor", MLFeatureValue.binary(false)),
                                                                Pair("diff_lines_with_class_def", MLFeatureValue.numerical(1)))

  fun testInsideClassAfterConstructor() = doContextFeaturesTest(Pair("containing_class_have_constructor", MLFeatureValue.binary(true)),
                                                                Pair("diff_lines_with_class_def", MLFeatureValue.numerical(4)))

  fun testNumOfPrevQualifiersIs3() = doContextFeaturesTest(Pair("num_of_prev_qualifiers", MLFeatureValue.numerical(3)))

  fun testNumOfPrevQualifiersIs4() = doContextFeaturesTest(Pair("num_of_prev_qualifiers", MLFeatureValue.numerical(4)))

  fun testNumOfPrevQualifiersIs1() = doContextFeaturesTest(Pair("num_of_prev_qualifiers", MLFeatureValue.numerical(1)))

  // Element features

  fun testDictKey() = doElementFeaturesTest("\"dict_key\"",
                                            Pair("is_dict_key", MLFeatureValue.binary(true)),
                                            Pair("underscore_type", MLFeatureValue.categorical(
                                              PyCompletionFeatures.ElementNameUnderscoreType.NO_UNDERSCORE)))

  fun testIsTakesParameterSelf() = doElementFeaturesTest(listOf(
    Pair("foo", listOf(Pair("is_takes_parameter_self", MLFeatureValue.binary(true)))),
    Pair("__init__", listOf(Pair("is_takes_parameter_self", MLFeatureValue.binary(true)))),
    Pair("bar", listOf(Pair("is_takes_parameter_self", MLFeatureValue.binary(false))))))

  fun testUnderscoreTypeTwoStartEnd() = doElementFeaturesTest("__init__",
                                                              Pair("underscore_type", MLFeatureValue.categorical(
                                                                PyCompletionFeatures.ElementNameUnderscoreType.TWO_START_END)))

  fun testUnderscoreTypeTwoStart() = doElementFeaturesTest(listOf(
    Pair("__private_var",
         listOf(Pair("underscore_type", MLFeatureValue.categorical(PyCompletionFeatures.ElementNameUnderscoreType.TWO_START)))),
    Pair("_private_var",
         listOf(Pair("underscore_type", MLFeatureValue.categorical(PyCompletionFeatures.ElementNameUnderscoreType.ONE_START))))))

  fun testNumberOfOccurrencesFunction() = doElementFeaturesTest("min",
                                                                Pair("number_of_occurrences_in_scope", MLFeatureValue.numerical(2)),
                                                                Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.FUNCTION)),
                                                                Pair("is_builtins", MLFeatureValue.binary(true)))

  fun testNumberOfOccurrencesClass() = doElementFeaturesTest("MyClazz",
                                                             Pair("number_of_occurrences_in_scope", MLFeatureValue.numerical(1)),
                                                             Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.TYPE_OR_CLASS)),
                                                             Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testNumberOfOccurrencesNamedArgsEmptyPrefix() = doElementFeaturesTest("file=",
                                                                            Pair("number_of_occurrences_in_scope", MLFeatureValue.numerical(0)),
                                                                            Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.NAMED_ARG)),
                                                                            Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testNumberOfOccurrencesPackagesOrModules() = doElementFeaturesTest("collections",
                                                                         Pair("number_of_occurrences_in_scope", MLFeatureValue.numerical(1)),
                                                                         Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.PACKAGE_OR_MODULE)),
                                                                         Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testKindNamedArg() = doElementFeaturesTest("sep=",
                                                 Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.NAMED_ARG)),
                                                 Pair("number_of_tokens", MLFeatureValue.numerical(1)),
                                                 Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testClassBuiltins() = doElementFeaturesTest("Exception",
                                                  Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.TYPE_OR_CLASS)),
                                                  Pair("number_of_tokens", MLFeatureValue.numerical(1)),
                                                  Pair("is_builtins", MLFeatureValue.binary(true)))

  fun testClassNotBuiltins() = doElementFeaturesTest("MyClazz",
                                                     Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.TYPE_OR_CLASS)),
                                                     Pair("number_of_tokens", MLFeatureValue.numerical(2)),
                                                     Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testFunctionBuiltins() = doElementFeaturesTest("max",
                                                     Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.FUNCTION)),
                                                     Pair("is_builtins", MLFeatureValue.binary(true)))

  fun testFunctionNotBuiltins() = doElementFeaturesTest("my_not_builtins_function",
                                                        Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.FUNCTION)),
                                                        Pair("number_of_tokens", MLFeatureValue.numerical(4)),
                                                        Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testKindPackageOrModule() = doElementFeaturesTest("sys",
                                                        Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.PACKAGE_OR_MODULE)),
                                                        Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testKindFromTargetAssignment() = doElementFeaturesTest("local_variable",
                                                    Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.FROM_TARGET)),
                                                    Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testKindFromTargetAs() = doElementFeaturesTest("as_target",
                                                    Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.FROM_TARGET)),
                                                    Pair("is_builtins", MLFeatureValue.binary(false)))

  fun testKindKeyword() = doElementFeaturesTest("if",
                                                arrayListOf(
                                                  Pair("kind", MLFeatureValue.categorical(PyCompletionMlElementKind.KEYWORD)),
                                                  Pair("keyword_id", MLFeatureValue.numerical(kwId("if"))),
                                                  Pair("is_builtins", MLFeatureValue.binary(false))),
                                                arrayListOf("standard_type"))

  fun testScopeMatchesSimple() = doElementFeaturesTest("abacaba",
                                                          Pair("scope_num_names", MLFeatureValue.numerical(5)),
                                                          Pair("scope_num_different_names", MLFeatureValue.numerical(3)),
                                                          Pair("scope_num_matches", MLFeatureValue.numerical(2)),
                                                          Pair("scope_num_tokens_matches", MLFeatureValue.numerical(2)))

  fun testScopeMatchesTokens() = doElementFeaturesTest("oneFourThreeTwo",
                                                       Pair("scope_num_names", MLFeatureValue.numerical(13)),
                                                       Pair("scope_num_different_names", MLFeatureValue.numerical(10)),
                                                       Pair("scope_num_matches", MLFeatureValue.numerical(1)),
                                                       Pair("scope_num_tokens_matches", MLFeatureValue.numerical(20)))

  fun testScopeMatchesNonEmptyPrefix() = doElementFeaturesTest("someParam1",
                                                       Pair("scope_num_names", MLFeatureValue.numerical(6)),
                                                       Pair("scope_num_different_names", MLFeatureValue.numerical(4)),
                                                       Pair("scope_num_matches", MLFeatureValue.numerical(2)),
                                                       Pair("scope_num_tokens_matches", MLFeatureValue.numerical(6)))

  fun testScopeFileDontConsiderFunctionBodies() = doElementFeaturesTest("SOME_VAR_3",
                                                               Pair("scope_num_names", MLFeatureValue.numerical(6)),
                                                               Pair("scope_num_different_names", MLFeatureValue.numerical(6)),
                                                               Pair("scope_num_matches", MLFeatureValue.numerical(1)),
                                                               Pair("scope_num_tokens_matches", MLFeatureValue.numerical(8)))

  fun testScopeClassDontConsiderFunctionBodies() = doElementFeaturesTest("SOME_VAR_3",
                                                                        Pair("scope_num_names", MLFeatureValue.numerical(7)),
                                                                        Pair("scope_num_different_names", MLFeatureValue.numerical(7)),
                                                                        Pair("scope_num_matches", MLFeatureValue.numerical(1)),
                                                                        Pair("scope_num_tokens_matches", MLFeatureValue.numerical(9)))

  fun testSameLineMatchingSimple() = doElementFeaturesTest("some_param3",
                                                                         Pair("same_line_num_names", MLFeatureValue.numerical(6)),
                                                                         Pair("same_line_num_different_names", MLFeatureValue.numerical(5)),
                                                                         Pair("same_line_num_matches", MLFeatureValue.numerical(0)),
                                                                         Pair("same_line_num_tokens_matches", MLFeatureValue.numerical(5)))

  fun testReceiverMatchesSimple() = doElementFeaturesTest("someParam",
                                                          Pair("receiver_name_matches", MLFeatureValue.binary(true)),
                                                          Pair("receiver_num_matched_tokens", MLFeatureValue.numerical(2)),
                                                          Pair("receiver_tokens_num", MLFeatureValue.numerical(2)))

  fun testReceiverMatchesTokens() = doElementFeaturesTest("someToken2_sets1",
                                                          Pair("receiver_name_matches", MLFeatureValue.binary(false)),
                                                          Pair("receiver_num_matched_tokens", MLFeatureValue.numerical(3)),
                                                          Pair("receiver_tokens_num", MLFeatureValue.numerical(3)))

  fun testReceiverMatchesAssignment() = doElementFeaturesTest("abaCaba",
                                                          Pair("receiver_name_matches", MLFeatureValue.binary(true)),
                                                          Pair("receiver_num_matched_tokens", MLFeatureValue.numerical(2)),
                                                          Pair("receiver_tokens_num", MLFeatureValue.numerical(2)))

  fun testReceiverMatchesTokensUpperCase() = doElementFeaturesTest("SOME_TOKENS_SET",
                                                          Pair("receiver_name_matches", MLFeatureValue.binary(false)),
                                                          Pair("receiver_num_matched_tokens", MLFeatureValue.numerical(3)),
                                                          Pair("receiver_tokens_num", MLFeatureValue.numerical(3)))

  fun testMatchesWithEnclosingMethodTheSameName() = doElementFeaturesTest(
    "_qwer_tyuio_asdf_gh",
    Pair("number_of_tokens", MLFeatureValue.numerical(4)),
    Pair("matches_with_enclosing_method", MLFeatureValue.binary(true)),
    Pair("matched_tokens_with_enclosing_method", MLFeatureValue.numerical(4))
  )

  fun testMatchesWithEnclosingMethodAlmostTheSameName() = doElementFeaturesTest(
    "_qwer_tyuio_asdf_gh",
    listOf(Pair("number_of_tokens", MLFeatureValue.numerical(4)),
    Pair("matched_tokens_with_enclosing_method", MLFeatureValue.numerical(4))),
    listOf("matches_with_enclosing_method")
  )

  fun testLocationSameFileAndMethodAndClass() = doElementFeaturesTest(
    "some_variable",
    Pair("is_the_same_file", MLFeatureValue.binary(true)),
    Pair("is_the_same_class", MLFeatureValue.binary(true)),
    Pair("is_the_same_method", MLFeatureValue.binary(true))
  )

  fun testLocationSameFileAndClass() = doElementFeaturesTest(
    "some_variable",
    listOf(Pair("is_the_same_file", MLFeatureValue.binary(true)), Pair("is_the_same_class", MLFeatureValue.binary(true))),
    listOf("is_the_same_method")
  )

  fun testLocationSameFileAndMethod() = doElementFeaturesTest(
    "some_variable",
    listOf(Pair("is_the_same_file", MLFeatureValue.binary(true)), Pair("is_the_same_method", MLFeatureValue.binary(true))),
    listOf("is_the_same_class")
  )

  fun testLocationSameFileOnly() = doElementFeaturesTest(
    "some_function",
    listOf(Pair("is_the_same_file", MLFeatureValue.binary(true))),
    listOf("is_the_same_class", "is_the_same_method")
  )

  fun testLocationDifferentFile() = doElementFeaturesTest(
    "min",
    emptyList(),
    listOf("is_the_same_file", "is_the_same_class", "is_the_same_method")
  )
}