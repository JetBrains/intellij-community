/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.refactoring.rename;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Avdeev
 */
public class RegExpValidator implements Condition<String> {

  private final String myPattern;

  public RegExpValidator(@NonNls String pattern) {
    myPattern = pattern;
  }

  public boolean value(final String object) {
    return object.matches(myPattern);
  }
}
