// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.PyDeprecationInspection;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.Nullable;

public class Py3DeprecationTest extends PyTestCase {

  public void testAbcDeprecatedAbstracts() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON34,
      () -> {
        myFixture.enableInspections(PyDeprecationInspection.class);
        myFixture.configureByFile("deprecation/abcDeprecatedAbstracts.py");
        myFixture.checkHighlighting(true, false, false);
      }
    );
  }

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyLatestDescriptor;
  }
}
