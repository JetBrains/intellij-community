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
package com.jetbrains.python.traceBackParsers;

import org.jetbrains.annotations.NotNull;

/**
 * Information about trace produced by {@link TraceBackParser}
 *
 * @author Ilya.Kazakevich
 */
public final class LinkInTrace {
  @NotNull
  private final String myFileName;
  private final int myLineNumber;
  private final int myStartPos;
  private final int myEndPos;

  /**
   * @param fileName   name of file this line has link to
   * @param lineNumber number of line in file
   * @param startPos   start position of link in line
   * @param endPos     end position of link in line
   */
  public LinkInTrace(@NotNull final String fileName, final int lineNumber, final int startPos, final int endPos) {
    myFileName = fileName;
    myLineNumber = lineNumber;
    myStartPos = startPos;
    myEndPos = endPos;
  }

  /**
   *
   * @return name of file this line has link to
   */
  @NotNull
  public String getFileName() {
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
