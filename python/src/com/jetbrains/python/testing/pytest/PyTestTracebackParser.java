// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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


  @Override
  public @Nullable LinkInTrace findLinkInTrace(final @NotNull String line) {
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

  private static @NotNull LinkInTrace createLinkInTrace(final @NotNull PyFilesStateMachine machine) {
    final Pair<String, String> fileAndLine = machine.getFileAndLine();
    final int start = machine.getStart();
    final String lineNumber = fileAndLine.second;
    // Cut too long lines that can't be presented as integer anyway
    final int number = Integer.parseInt((lineNumber.length() > 5 ? lineNumber.substring(0, 5) : lineNumber));
    return new LinkInTrace(fileAndLine.first, number, start, start + fileAndLine.first.length() + lineNumber.length() + 1);
  }
}
