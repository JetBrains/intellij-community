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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * User : catherine
 */
public final class PyTestTracebackParser implements TraceBackParser {


  @Nullable
  @Override
  public LinkInTrace findLinkInTrace(@NotNull final String line) {
    final Collection<PyFilesStateMachine> machines = new ArrayList<PyFilesStateMachine>();
    machines.add(PyFilesStateMachine.createFromStart());
    final char[] chars = line.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      final char nextChar = chars[i];
      final Iterator<PyFilesStateMachine> iterator = machines.iterator();
      while (iterator.hasNext()) {
        final PyFilesStateMachine machine = iterator.next();
        switch (machine.addChar(nextChar)) {
          case FAILED:
            iterator.remove(); // Machine failed, discard
            break;
          case FINISHED:
            return createLinkInTrace(machine); // Link found
          case IN_PROGRESS:
            break;
        }
      }

      final PyFilesStateMachine newMachine = PyFilesStateMachine.createFromChar(nextChar, i);
      if (newMachine != null) {
        machines.add(newMachine);
      }
    }

    // Tell all machines file is ended
    for (final PyFilesStateMachine machine : machines) {
      if (machine.endLine() == PyFilesStateMachineResult.FINISHED) {
        return createLinkInTrace(machine);
      }
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
