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

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author Mikhail Golubev
 */
public class PyConvertCollectionLiteralIntentionTest extends PyIntentionTestCase {
  // PY-9419
  public void testConvertParenthesizedTupleToList() {
    doIntentionTest(getConvertTupleToList());
  }

  // PY-9419
  public void testConvertTupleWithoutParenthesesToList() {
    doIntentionTest(getConvertTupleToList());
  }

  // PY-9419
  public void testConvertTupleWithoutClosingParenthesisToList() {
    doIntentionTest(getConvertTupleToList());
  }

  // PY-9419
  public void testConvertParenthesizedTupleToSet() {
    doIntentionTest(getConvertTupleToSet());
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableWithoutSetLiterals() {
    runWithLanguageLevel(LanguageLevel.PYTHON26, () -> doNegativeTest(getConvertTupleToSet()));
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInAssignmentTarget() {
    doNegativeTest(getConvertTupleToSet());
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInForLoop() {
    doNegativeTest(getConvertTupleToSet());
  }

  // PY-9419
  public void testConvertTupleToSetNotAvailableInComprehension() {
    doNegativeTest(getConvertTupleToSet());
  }

  // PY-9419
  public void testConvertListToTuple() {
    doIntentionTest(getConvertListToTuple());
  }

  // PY-9419
  public void testConvertListWithoutClosingBracketToTuple() {
    doIntentionTest(getConvertListToTuple());
  }

  // PY-9419
  public void testConvertListToSet() {
    doIntentionTest(getConvertListToSet());
  }

  // PY-9419
  public void testConvertSetToTuple() {
    doIntentionTest(getConvertSetToTuple());
  }

  // PY-9419
  public void testConvertSetWithoutClosingBraceToTuple() {
    doIntentionTest(getConvertSetToTuple());
  }

  // PY-9419
  public void testConvertSetToList() {
    doIntentionTest(getConvertSetToList());
  }

  // PY-16335
  public void testConvertLiteralPreservesFormattingAndComments() {
    doIntentionTest(getConvertTupleToList());
  }

  // PY-16553
  public void testConvertOneElementListToTuple() {
    doIntentionTest(getConvertListToTuple());
  }
  
  // PY-16553
  public void testConvertOneElementIncompleteListToTuple() {
    doIntentionTest(getConvertListToTuple());
  }

  // PY-16553
  public void testConvertOneElementListWithCommentToTuple() {
    doIntentionTest(getConvertListToTuple());
  }
  
  // PY-16553
  public void testConvertOneElementListWithCommaAfterCommentToTuple() {
    doIntentionTest(getConvertListToTuple());
  }

  // PY-16553
  public void testConvertOneElementTupleToList() {
    doIntentionTest(getConvertTupleToList());
  }

  // PY-16553
  public void testConvertOneElementTupleWithoutParenthesesToSet() {
    doIntentionTest(getConvertTupleToSet());
  }

  // PY-16553
  public void testConvertOneElementTupleWithCommentToList() {
    doIntentionTest(getConvertTupleToList());
  }

  // PY-19399
  public void testCannotConvertEmptyTupleToSet() {
    doNegativeTest(getConvertTupleToSet());
  }
  
  // PY-19399
  public void testCannotConvertEmptyListToSet() {
    doNegativeTest(getConvertListToSet());
  }

  private static String getConvertTupleToList() {
    return PyPsiBundle.message("INTN.convert.collection.literal", "tuple", "list");
  }

  private static String getConvertTupleToSet() {
    return PyPsiBundle.message("INTN.convert.collection.literal", "tuple", "set");
  }

  private static String getConvertListToTuple() {
    return PyPsiBundle.message("INTN.convert.collection.literal", "list", "tuple");
  }

  private static String getConvertListToSet() {
    return PyPsiBundle.message("INTN.convert.collection.literal", "list", "set");
  }

  private static String getConvertSetToTuple() {
    return PyPsiBundle.message("INTN.convert.collection.literal", "set", "tuple");
  }

  private static String getConvertSetToList() {
    return PyPsiBundle.message("INTN.convert.collection.literal", "set", "list");
  }
}
