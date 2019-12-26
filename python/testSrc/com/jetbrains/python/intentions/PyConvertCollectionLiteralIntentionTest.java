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
  // PY-9419
  public void testConvertParenthesizedTupleToList() {
    doIntentionTest(getCONVERT_TUPLE_TO_LIST());
  }

  // PY-9419
  public void testConvertTupleWithoutParenthesesToList() {
    doIntentionTest(getCONVERT_TUPLE_TO_LIST());
  }

  // PY-9419
  public void testConvertTupleWithoutClosingParenthesisToList() {
    doIntentionTest(getCONVERT_TUPLE_TO_LIST());
  }

  // PY-9419
  public void testConvertParenthesizedTupleToSet() {
    doIntentionTest(getCONVERT_TUPLE_TO_SET());
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableWithoutSetLiterals() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doNegativeTest(getCONVERT_TUPLE_TO_SET()));
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInAssignmentTarget() {
    doNegativeTest(getCONVERT_TUPLE_TO_SET());
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInForLoop() {
    doNegativeTest(getCONVERT_TUPLE_TO_SET());
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInComprehension() {
    doNegativeTest(getCONVERT_TUPLE_TO_SET());
  }

  // PY-9419
  public void testConvertListToTuple() {
    doIntentionTest(getCONVERT_LIST_TO_TUPLE());
  }

  // PY-9419
  public void testConvertListWithoutClosingBracketToTuple() {
    doIntentionTest(getCONVERT_LIST_TO_TUPLE());
  }

  // PY-9419
  public void testConvertListToSet() {
    doIntentionTest(getCONVERT_LIST_TO_SET());
  }

  // PY-9419
  public void testConvertSetToTuple() {
    doIntentionTest(getCONVERT_SET_TO_TUPLE());
  }

  // PY-9419
  public void testConvertSetWithoutClosingBraceToTuple() {
    doIntentionTest(getCONVERT_SET_TO_TUPLE());
  }

  // PY-9419
  public void testConvertSetToList() {
    doIntentionTest(getCONVERT_SET_TO_LIST());
  }

  // PY-16335
  public void testConvertLiteralPreservesFormattingAndComments() {
    doIntentionTest(getCONVERT_TUPLE_TO_LIST());
  }

  // PY-16553
  public void testConvertOneElementListToTuple() {
    doIntentionTest(getCONVERT_LIST_TO_TUPLE());
  }
  
  // PY-16553
  public void testConvertOneElementIncompleteListToTuple() {
    doIntentionTest(getCONVERT_LIST_TO_TUPLE());
  }

  // PY-16553
  public void testConvertOneElementListWithCommentToTuple() {
    doIntentionTest(getCONVERT_LIST_TO_TUPLE());
  }
  
  // PY-16553
  public void testConvertOneElementListWithCommaAfterCommentToTuple() {
    doIntentionTest(getCONVERT_LIST_TO_TUPLE());
  }

  // PY-16553
  public void testConvertOneElementTupleToList() {
    doIntentionTest(getCONVERT_TUPLE_TO_LIST());
  }

  // PY-16553
  public void testConvertOneElementTupleWithoutParenthesesToSet() {
    doIntentionTest(getCONVERT_TUPLE_TO_SET());
  }

  // PY-16553
  public void testConvertOneElementTupleWithCommentToList() {
    doIntentionTest(getCONVERT_TUPLE_TO_LIST());
  }

  // PY-19399
  public void testCannotConvertEmptyTupleToSet() {
    doNegativeTest(getCONVERT_TUPLE_TO_SET());
  }
  
  // PY-19399
  public void testCannotConvertEmptyListToSet() {
    doNegativeTest(getCONVERT_LIST_TO_SET());
  }

  private static String getCONVERT_TUPLE_TO_LIST() {
    return PyBundle.message("INTN.convert.collection.literal.text", "tuple", "list");
  }

  private static String getCONVERT_TUPLE_TO_SET() {
    return PyBundle.message("INTN.convert.collection.literal.text", "tuple", "set");
  }

  private static String getCONVERT_LIST_TO_TUPLE() {
    return PyBundle.message("INTN.convert.collection.literal.text", "list", "tuple");
  }

  private static String getCONVERT_LIST_TO_SET() {
    return PyBundle.message("INTN.convert.collection.literal.text", "list", "set");
  }

  private static String getCONVERT_SET_TO_TUPLE() {
    return PyBundle.message("INTN.convert.collection.literal.text", "set", "tuple");
  }

  private static String getCONVERT_SET_TO_LIST() {
    return PyBundle.message("INTN.convert.collection.literal.text", "set", "list");
  }
}
