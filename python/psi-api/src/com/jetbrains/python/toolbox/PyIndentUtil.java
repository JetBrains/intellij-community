/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.toolbox;

import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class PyIndentUtil {
  private PyIndentUtil() {
  }

  public static int getLineIndentSize(@NotNull CharSequence line) {
    return getLineIndent(line).length();
  }

  @NotNull
  public static CharSequence getLineIndent(@NotNull CharSequence line) {
    int stop;
    for (stop = 0; stop < line.length(); stop++) {
      final char c = line.charAt(stop);
      if (c == ' ' || c == '\t') {
        break;
      }
    }
    return line.subSequence(0, stop);
  }
}
