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
package com.jetbrains.python.documentation.docstrings;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class DocStringUpdater<T extends DocStringLineParser> {
  protected final T myOriginalDocString;
  private final StringBuilder myBuilder;
  private final List<Modification> myUpdates = new ArrayList<>();
  protected final String myMinContentIndent;

  public DocStringUpdater(@NotNull T docString, @NotNull String minContentIndent) {
    myBuilder = new StringBuilder(docString.getDocStringContent().getSuperString());
    myOriginalDocString = docString;
    myMinContentIndent = minContentIndent;
  }

  protected final void replace(@NotNull TextRange range, @NotNull String text) {
    myUpdates.add(new Modification(range, text));
  }

  protected final void replace(int startOffset, int endOffset, @NotNull String text) {
    replace(new TextRange(startOffset, endOffset), text);
  }

  protected final void insert(int offset, @NotNull String text) {
    replace(offset, offset, text);
  }

  protected final void insertAfterLine(int lineNumber, @NotNull String text) {
    final Substring line = myOriginalDocString.getLines().get(lineNumber);
    insert(line.getEndOffset(), '\n' + text);
  }

  protected final void remove(int startOffset, int endOffset) {
    replace(startOffset, endOffset, "");
  }

  /**
   * @param startLine inclusive
   * @param endLine exclusive
   */
  protected final void removeLines(int startLine, int endLine) {
    final List<Substring> lines = myOriginalDocString.getLines();
    final int startOffset = lines.get(startLine).getStartOffset();
    final int endOffset = endLine < lines.size() ? lines.get(endLine).getStartOffset() : lines.get(endLine - 1).getEndOffset();
    remove(startOffset, endOffset);
  }

  protected final void removeLinesAndSpacesAfter(int startLine, int endLine) {
    removeLines(startLine, skipEmptyLines(endLine));
  }

  private int skipEmptyLines(int startLine) {
    return Math.min(myOriginalDocString.consumeEmptyLines(startLine), myOriginalDocString.getLineCount() - 1);
  }

  protected final void removeLine(int line) {
    removeLines(line, line + 1);
  }

  protected final void insertBeforeLine(int lineNumber, @NotNull String text) {
    final Substring line = myOriginalDocString.getLines().get(lineNumber);
    insert(line.getStartOffset(), text + '\n');
  }

  @NotNull
  public final String getDocStringText() {
    beforeApplyingModifications();
    // Move closing quotes to the next line, if new lines are going to be inserted
    if (myOriginalDocString.getLineCount() == 1 && !myUpdates.isEmpty()) {
      insertAfterLine(0, myMinContentIndent);
    }
    // If several updates insert in one place (e.g. new field), insert them in backward order,
    // so the first added is placed above
    Collections.reverse(myUpdates);
    Collections.sort(myUpdates, Collections.reverseOrder());
    for (final Modification update : myUpdates) {
      final TextRange updateRange = update.range;
      if (updateRange.getStartOffset() == updateRange.getEndOffset()) {
        myBuilder.insert(updateRange.getStartOffset(), update.text);
      }
      else {
        myBuilder.replace(updateRange.getStartOffset(), updateRange.getEndOffset(), update.text);
      }
    }
    return myBuilder.toString();
  }

  protected void beforeApplyingModifications() {
    
  }

  @NotNull
  public T getOriginalDocString() {
    return myOriginalDocString;
  }

  @NotNull
  protected String getLineIndent(int lineNum) {
    final String lastLineIndent = myOriginalDocString.getLineIndent(lineNum);
    if (PyIndentUtil.getLineIndentSize(lastLineIndent) < PyIndentUtil.getLineIndentSize(myMinContentIndent)) {
      return myMinContentIndent;
    }
    return lastLineIndent;
  }

  protected int getLineIndentSize(int lineNum) {
    return PyIndentUtil.getLineIndentSize(getLineIndent(lineNum));
  }

  protected int findLastNonEmptyLine() {
    for (int i = myOriginalDocString.getLineCount() - 1; i >= 0; i--) {
      if (!StringUtil.isEmptyOrSpaces(myOriginalDocString.getLine(i))) {
        return i;
      }
    }
    return 0;
  }

  public abstract void addParameter(@NotNull String name, @Nullable String type);

  public abstract void addReturnValue(@Nullable String type);

  public abstract void removeParameter(@NotNull String name);

  private static class Modification implements Comparable<Modification> {
    @NotNull final TextRange range;
    @NotNull final String text;

    public Modification(@NotNull TextRange range, @NotNull String newText) {
      this.range = range;
      this.text = newText;
    }

    @Override
    public int compareTo(Modification o) {
      return range.getStartOffset() - o.range.getStartOffset();
    }
  }
}
