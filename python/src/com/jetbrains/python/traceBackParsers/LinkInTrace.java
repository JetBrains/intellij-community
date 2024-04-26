// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.traceBackParsers;

import org.jetbrains.annotations.NotNull;

/**
 * Information about trace produced by {@link TraceBackParser}
 *
 * @author Ilya.Kazakevich
 */
public final class LinkInTrace {
  private final @NotNull String myFileName;
  private final int myLineNumber;
  private final int myStartPos;
  private final int myEndPos;

  /**
   * @param fileName   name of file this line has link to
   * @param lineNumber number of line in file
   * @param startPos   start position of link in line
   * @param endPos     end position of link in line
   */
  public LinkInTrace(final @NotNull String fileName, final int lineNumber, final int startPos, final int endPos) {
    myFileName = fileName;
    myLineNumber = lineNumber;
    myStartPos = startPos;
    myEndPos = endPos;
  }

  /**
   *
   * @return name of file this line has link to
   */
  public @NotNull String getFileName() {
    return myFileName;
  }

  /**
   *
   * @return number of line in file
   */
  public int getLineNumber() {
    return myLineNumber;
  }

  /**
   *
   * @return start position of link in line
   */
  public int getStartPos() {
    return myStartPos;
  }

  /**
   *
   * @return end position of link in line
   */
  public int getEndPos() {
    return myEndPos;
  }

  @Override
  public String toString() {
    return "LinkInTrace{" +
           "myFileName='" + myFileName + '\'' +
           ", myLineNumber=" + myLineNumber +
           ", myStartPos=" + myStartPos +
           ", myEndPos=" + myEndPos +
           '}';
  }
}
