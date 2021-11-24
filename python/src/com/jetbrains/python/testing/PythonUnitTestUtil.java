// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThreeState;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PythonUnitTestUtil {
  private PythonUnitTestUtil() {
  }

  /**
   * @deprecated use {@link PythonUnitTestDetectorsBasedOnSettings#isTestClass(PyClass, ThreeState, TypeEvalContext)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public static boolean isTestClass(@NotNull final PyClass cls,
                                    @NotNull final ThreeState testCaseClassRequired,
                                    @Nullable TypeEvalContext context) {
    Logger.getInstance(PythonUnitTestUtil.class).warn("Please to not use deprecated class "  + PythonUnitTestUtil.class.getCanonicalName());
    return PythonUnitTestDetectorsBasedOnSettings.isTestClass(cls, testCaseClassRequired, context);
  }
}
