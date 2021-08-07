// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes;

import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.psi.LanguageLevel;

import java.util.ListResourceBundle;

public class PyRemoveTrailingSuffixQuickFixTest extends PyQuickFixTestCase {

  private static final ListResourceBundle MESSAGE_BUNDLE = new ListResourceBundle() {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {
        new Object[] { "bad-trail", "Python version " + LanguageLevel.getLatest() + " does not support a trailing ''{0}''." }
      };
    }
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((CodeInsightTestFixtureImpl)myFixture).setMessageBundles(MESSAGE_BUNDLE);
  }

  public void testFixl() {
    doQuickFixTest(PyPsiBundle.message("QFIX.remove.trailing.suffix"));
  }
}
