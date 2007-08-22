/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextRange {
  private final int myStartOffset;
  private final int myEndOffset;

  public TextRange(int startOffset, int endOffset) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  public final int getStartOffset() {
    return myStartOffset;
  }

  public final int getEndOffset() {
    return myEndOffset;
  }

  public final int getLength() {
    return myEndOffset - myStartOffset;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof TextRange)) return false;
    TextRange range = (TextRange)obj;
    return myStartOffset == range.myStartOffset && myEndOffset == range.myEndOffset;
  }

  public int hashCode() {
    return myStartOffset + myEndOffset;
  }

  public boolean contains(TextRange anotherRange) {
    return myStartOffset <= anotherRange.getStartOffset() && myEndOffset >= anotherRange.getEndOffset();
  }

  public String toString() {
    return "(" + myStartOffset + "," + myEndOffset + ")";
  }

  public boolean contains(int offset) {
    return myStartOffset <= offset && offset < myEndOffset;
  }

  @NotNull
  public String substring(@NotNull String str) {
    return str.substring(myStartOffset, myEndOffset);
  }

  @NotNull
  public TextRange cutOut(@NotNull TextRange subRange) {
    assert subRange.getStartOffset() <= getLength() : subRange + "; this="+this;
    assert subRange.getEndOffset() <= getLength() : subRange + "; this="+this;
    return new TextRange(myStartOffset + subRange.getStartOffset(), Math.min(myEndOffset, myStartOffset + subRange.getEndOffset()));
  }

  @NotNull
  public TextRange shiftRight(int offset) {
    if (offset == 0) return this;
    return new TextRange(myStartOffset + offset, myEndOffset + offset);
  }

  @NotNull
  public TextRange grown(int lengthDelta) {
    return from(myStartOffset, getLength() + lengthDelta);
  }

  @NotNull
  public static TextRange from(int offset, int length) {
    return new TextRange(offset, offset + length);
  }

  @NotNull
  public String replace(@NotNull String original, @NotNull String replacement) {
    String beginning = original.substring(0, getStartOffset());
    String ending = original.substring(getEndOffset(), original.length());
    return beginning + replacement + ending;
  }

  public boolean intersects(@NotNull TextRange textRange) {
    return Math.max(myStartOffset, textRange.getStartOffset()) <= Math.min(myEndOffset, textRange.getEndOffset());
  }
  public boolean intersectsStrict(@NotNull TextRange textRange) {
    return Math.max(myStartOffset, textRange.getStartOffset()) < Math.min(myEndOffset, textRange.getEndOffset());
  }

  @Nullable
  public TextRange intersection(@NotNull TextRange range) {
    if (!intersects(range)) return null;
    return new TextRange(Math.max(myStartOffset, range.getStartOffset()), Math.min(myEndOffset, range.getEndOffset()));
  }

  public boolean isEmpty() {
    return myStartOffset >= myEndOffset;
  }

  public TextRange union(TextRange textRange) {
    return new TextRange(Math.min(myStartOffset, textRange.getStartOffset()), Math.max(myEndOffset, textRange.getEndOffset()));
  }
}
