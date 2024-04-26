// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.jetbrains.python.traceBackParsers.LinkInTrace;
import com.jetbrains.python.traceBackParsers.TraceBackParserAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds links in default python traceback
 *
 * @author Ilya.Kazakevich
 */
public class PyTracebackParser extends TraceBackParserAdapter {


  public PyTracebackParser() {
    // File name can't start with number, can't be more then 200 chars long (its insane) and line number is also limited to int maxvalue
    super(Pattern.compile("File \"([^0-9][^\"]{0,200})\", line (\\d{1,8})"));
  }

  @Override
  protected @NotNull LinkInTrace findLinkInTrace(final @NotNull String line, final @NotNull Matcher matchedMatcher) {
    final String fileName = matchedMatcher.group(1).replace('\\', '/');
    final int lineNumber = Integer.parseInt(matchedMatcher.group(2));
    final int startPos = line.indexOf('\"') + 1;
    final int endPos = line.indexOf('\"', startPos);
    return new LinkInTrace(fileName, lineNumber, startPos, endPos);
  }
}
