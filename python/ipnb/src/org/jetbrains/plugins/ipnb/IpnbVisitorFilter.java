/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.ipnb;

import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.PyIncorrectDocstringInspection;
import com.jetbrains.python.inspections.PyMissingOrEmptyDocstringInspection;
import com.jetbrains.python.inspections.PyStatementEffectInspection;
import com.jetbrains.python.inspections.PythonVisitorFilter;
import org.jetbrains.annotations.NotNull;

public class IpnbVisitorFilter implements PythonVisitorFilter {
  @Override
  public boolean isSupported(@NotNull final Class visitorClass, @NotNull final PsiFile file) {
    if (visitorClass == PyIncorrectDocstringInspection.class || 
        visitorClass == PyMissingOrEmptyDocstringInspection.class || 
        visitorClass == PyStatementEffectInspection.class) {
      return false;
    }
    return true;
  }
}
