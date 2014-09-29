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
package org.jetbrains.idea.svn.auth;

import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Kolosovsky.
 */
public enum AcceptResult {

  REJECTED("r"),
  ACCEPTED_TEMPORARILY("t"),
  ACCEPTED_PERMANENTLY("p");

  // cache all values as values() method returns new array on each call
  private static final AcceptResult[] allValues = values();

  @NotNull private final String code;

  AcceptResult(@NotNull String code) {
    this.code = code;
  }

  @Override
  public String toString() {
    return code;
  }

  @NotNull
  public static AcceptResult from(int value) {
    if (value < 0 || value >= allValues.length) {
      throw new IllegalArgumentException("Unknown AcceptResult - " + value);
    }

    return allValues[value];
  }
}
