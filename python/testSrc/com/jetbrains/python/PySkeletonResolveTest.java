// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyLightProjectDescriptor;
import com.jetbrains.python.fixtures.PyMultiFileResolveTestCase;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yuli Fiterman
 */
public class PySkeletonResolveTest extends PyMultiFileResolveTestCase {
  protected static final PyLightProjectDescriptor ourPyDescriptor = new PyLightProjectDescriptor("WithBinaryModules");

  @Nullable
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyDescriptor;
  }

  //PY-28629
  public void testOldNumpyRelativeImport() {
    assertInstanceOf(doResolve(), PyFunction.class);
  }
}
