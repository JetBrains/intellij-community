// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyAnnotateVariableTypeIntentionTest extends PyIntentionTestCase {
  public void testAnnotationLocalAssignmentTarget() {
    doTestAnnotation();
  }

  private void doTestAnnotation() {
    doTest(LanguageLevel.PYTHON36);
  }

  private void doTestTypeComment() {
    doTest(LanguageLevel.PYTHON27);
  }

  private void doTest(@NotNull LanguageLevel languageLevel) {
    doTest(PyBundle.message("INTN.annotate.types"), languageLevel);
  }
}
