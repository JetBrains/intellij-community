/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.run.RunnableScriptFilter;

/**
 * @author Ilya.Kazakevich
 */
public class PythonUnitTestRunnableScriptFilter {

  /**
   * @deprecated Use {@link RunnableUnitTestFilter#isRunnableUnitTest(PsiFile, Module)} instead. Delete after 2017.2
   */
  @Deprecated
  public static boolean isIfNameMain(Location location) {
    return RunnableScriptFilter.isIfNameMain(location);
  }
}
