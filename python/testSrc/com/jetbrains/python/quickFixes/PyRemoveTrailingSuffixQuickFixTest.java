// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.quickFixes;

import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

@Subsystems.QuickFixes
@Layers.Functional
public class PyRemoveTrailingSuffixQuickFixTest extends PyQuickFixTestCase {

  public void testFixl() {
    doQuickFixTest(PyPsiBundle.message("QFIX.remove.trailing.suffix"));
  }
}
