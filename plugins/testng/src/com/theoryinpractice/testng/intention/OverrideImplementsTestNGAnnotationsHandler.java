/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 19-Aug-2008
 */
package com.theoryinpractice.testng.intention;

import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsHandler;
import com.intellij.util.ArrayUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

public class OverrideImplementsTestNGAnnotationsHandler implements OverrideImplementsAnnotationsHandler{
  public String[] getAnnotations() {
    return TestNGUtil.CONFIG_ANNOTATIONS_FQN;
  }


  @NotNull
  public String[] annotationsToRemove(@NotNull final String fqName) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }
}