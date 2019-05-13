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
package com.jetbrains.python.testing.pytest;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.traceBackParsers.LinkInTrace;
import com.jetbrains.python.traceBackParsers.TraceBackParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public final class PyTestTracebackParser implements TraceBackParser {


  @Nullable
  @Override
  public LinkInTrace findLinkInTrace(@NotNull final String line) {
    final PyFilesStateMachine quoteMachine = new PyFilesStateMachine(true);
    final PyFilesStateMachine spaceMachine = new PyFilesStateMachine(false);


    final char[] chars = line.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      final char nextChar = chars[i];
      if (quoteMachine.addChar(nextChar, i)) {
        return createLinkInTrace(quoteMachine);
      }
      if (spaceMachine.addChar(nextChar, i)) {
        return createLinkInTrace(spaceMachine);
      }
    }

    // Tell all machines file is ended
    if (quoteMachine.endLine()) {
      return createLinkInTrace(quoteMachine);
    }
    if (spaceMachine.endLine()) {
      return createLinkInTrace(spaceMachine);
    }

    return null;
  }

  @NotNull
  private static LinkInTrace createLinkInTrace(@NotNull final PyFilesStateMachine machine) {
    final Pair<String, String> fileAndLine = machine.getFileAndLine();
    final int start = machine.getStart();
    final String lineNumber = fileAndLine.second;
    // Cut too long lines that can't be presented as integer anyway
    final int number = Integer.parseInt((lineNumber.length() > 5 ? lineNumber.substring(0, 5) : lineNumber));
    return new LinkInTrace(fileAndLine.first, number, start, start + fileAndLine.first.length() + lineNumber.length() + 1);
  }
}
