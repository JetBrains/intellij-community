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
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author Mikhail Golubev
 */
public class PyConvertCollectionLiteralIntentionTest extends PyIntentionTestCase {

  private static final String CONVERT_TUPLE_TO_LIST = PyBundle.message("INTN.convert.collection.literal.text", "tuple", "list");
  private static final String CONVERT_TUPLE_TO_SET = PyBundle.message("INTN.convert.collection.literal.text", "tuple", "set");
  private static final String CONVERT_LIST_TO_TUPLE = PyBundle.message("INTN.convert.collection.literal.text", "list", "tuple");
  private static final String CONVERT_LIST_TO_SET = PyBundle.message("INTN.convert.collection.literal.text", "list", "set");
  private static final String CONVERT_SET_TO_TUPLE = PyBundle.message("INTN.convert.collection.literal.text", "set", "tuple");
  private static final String CONVERT_SET_TO_LIST = PyBundle.message("INTN.convert.collection.literal.text", "set", "list");

  // PY-9419
  public void testConvertParenthesizedTupleToList() {
    doIntentionTest(CONVERT_TUPLE_TO_LIST);
  }

  // PY-9419
  public void testConvertTupleWithoutParenthesesToList() {
    doIntentionTest(CONVERT_TUPLE_TO_LIST);
  }

  // PY-9419
  public void testConvertTupleWithoutClosingParenthesisToList() {
    doIntentionTest(CONVERT_TUPLE_TO_LIST);
  }

  // PY-9419
  public void testConvertParenthesizedTupleToSet() {
    doIntentionTest(CONVERT_TUPLE_TO_SET);
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableWithoutSetLiterals() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doNegativeTest(CONVERT_TUPLE_TO_SET));
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInAssignmentTarget() {
    doNegativeTest(CONVERT_TUPLE_TO_SET);
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInForLoop() {
    doNegativeTest(CONVERT_TUPLE_TO_SET);
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInComprehension() {
    doNegativeTest(CONVERT_TUPLE_TO_SET);
  }

  // PY-9419
  public void testConvertListToTuple() {
    doIntentionTest(CONVERT_LIST_TO_TUPLE);
  }

  // PY-9419
  public void testConvertListWithoutClosingBracketToTuple() {
    doIntentionTest(CONVERT_LIST_TO_TUPLE);
  }

  // PY-9419
  public void testConvertListToSet() {
    doIntentionTest(CONVERT_LIST_TO_SET);
  }

  // PY-9419
  public void testConvertSetToTuple() {
    doIntentionTest(CONVERT_SET_TO_TUPLE);
  }

  // PY-9419
  public void testConvertSetWithoutClosingBraceToTuple() {
    doIntentionTest(CONVERT_SET_TO_TUPLE);
  }

  // PY-9419
  public void testConvertSetToList() {
    doIntentionTest(CONVERT_SET_TO_LIST);
  }

  // PY-16335
  public void testConvertLiteralPreservesFormattingAndComments() {
    doIntentionTest(CONVERT_TUPLE_TO_LIST);
  }

  // PY-16553
  public void testConvertOneElementListToTuple() {
    doIntentionTest(CONVERT_LIST_TO_TUPLE);
  }
  
  // PY-16553
  public void testConvertOneElementIncompleteListToTuple() {
    doIntentionTest(CONVERT_LIST_TO_TUPLE);
  }

  // PY-16553
  public void testConvertOneElementListWithCommentToTuple() {
    doIntentionTest(CONVERT_LIST_TO_TUPLE);
  }
  
  // PY-16553
  public void testConvertOneElementListWithCommaAfterCommentToTuple() {
    doIntentionTest(CONVERT_LIST_TO_TUPLE);
  }

  // PY-16553
  public void testConvertOneElementTupleToList() {
    doIntentionTest(CONVERT_TUPLE_TO_LIST);
  }

  // PY-16553
  public void testConvertOneElementTupleWithoutParenthesesToSet() {
    doIntentionTest(CONVERT_TUPLE_TO_SET);
  }

  // PY-16553
  public void testConvertOneElementTupleWithCommentToList() {
    doIntentionTest(CONVERT_TUPLE_TO_LIST);
  }

  // PY-19399
  public void testCannotConvertEmptyTupleToSet() {
    doNegativeTest(CONVERT_TUPLE_TO_SET);
  }
  
  // PY-19399
  public void testCannotConvertEmptyListToSet() {
    doNegativeTest(CONVERT_LIST_TO_SET);
  }
}
