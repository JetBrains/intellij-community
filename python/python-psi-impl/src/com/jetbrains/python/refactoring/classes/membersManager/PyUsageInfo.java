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

import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: Make it generic to allow to reuse in another projects?
 * Usage info that displays destination (where should member be moved)
 *
 * @author Ilya.Kazakevich
 */
class PyUsageInfo extends UsageInfo {
  @NotNull
  private final PyClass myTo;

  PyUsageInfo(@NotNull final PyClass to) {
    super(to, true); //TODO: Make super generic and get rid of field?
    myTo = to;
  }

  @NotNull
  public PyClass getTo() {
    return myTo;
  }
}
