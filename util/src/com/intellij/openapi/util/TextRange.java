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

  public String substring(String str) {
    return str.substring(myStartOffset, myEndOffset);
  }

  public TextRange cutOut(TextRange subRange) {
    return new TextRange(myStartOffset + subRange.getStartOffset(), Math.min(myEndOffset, myStartOffset + subRange.getEndOffset()));
  }

  public TextRange shiftRight(int offset) {
    if (offset == 0) return this;
    return new TextRange(myStartOffset + offset, myEndOffset + offset);
  }

  public TextRange grown(int lengthDelta) {
    return from(myStartOffset, getLength() + lengthDelta);
  }

  public static TextRange from(int offset, int length) {
    return new TextRange(offset, offset + length);
  }

  public String replace(String original, String replacement) {
    String beginning = original.substring(0, getStartOffset());
    String ending = original.substring(getEndOffset(), original.length());
    return beginning + replacement + ending;
  }

  public boolean intersects(@NotNull TextRange textRange) {
    return Math.max(myStartOffset, textRange.getStartOffset()) <= Math.min(myEndOffset, textRange.getEndOffset());
  }
  public boolean intersectsStrict(TextRange textRange) {
    return Math.max(myStartOffset, textRange.getStartOffset()) < Math.min(myEndOffset, textRange.getEndOffset());
  }

  @Nullable
  public TextRange intersection(final TextRange range) {
    if (!intersects(range)) return null;
    return new TextRange(Math.max(myStartOffset, range.getStartOffset()), Math.min(myEndOffset, range.getEndOffset()));
  }
}
