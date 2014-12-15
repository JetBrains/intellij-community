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
package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.psi.PsiNamedElement;
import com.jetbrains.NotNullPredicate;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

/**
 * Filters out named elements (ones that subclasses {@link com.intellij.psi.PsiNamedElement}) and {@link com.jetbrains.python.psi.PyElement})
 * that are null or has null name.
 * You need it sometimes when code has errors (i.e. bad formatted code with annotation may treat annotation as method with null name.
 *
* @author Ilya.Kazakevich
*/
class NamelessFilter<T extends PyElement & PsiNamedElement> extends NotNullPredicate<T> {

  @Override
  public boolean applyNotNull(@NotNull final T input) {
    return input.getName() != null;
  }
}
