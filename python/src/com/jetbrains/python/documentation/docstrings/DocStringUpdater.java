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
import com.jetbrains.python.documentation.DocStringLineParser;
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
  private final List<UpdateOperation> myUpdates = new ArrayList<UpdateOperation>();
  protected final List<AddParameter> myAddParameterRequests = new ArrayList<AddParameter>();
  protected final List<AddException> myAddExceptionRequest = new ArrayList<AddException>();
  protected final List<AddReturnType> myAddReturnTypeRequests = new ArrayList<AddReturnType>();

  public DocStringUpdater(@NotNull T docString) {
    myBuilder = new StringBuilder(docString.getDocStringContent().getSuperString());
    myOriginalDocString = docString;
  }

  public final void addParameter(@NotNull String name, @Nullable String type) {
    myAddParameterRequests.add(new AddParameter(name, type));
  }

  public final void addReturnType(@Nullable String name, @NotNull String type) {
    myAddReturnTypeRequests.add(new AddReturnType(name, type));
  }

  public final void addException(@NotNull String type) {
    myAddExceptionRequest.add(new AddException(type));
  }

  protected void insert(int offset, @NotNull String text) {
    myUpdates.add(new UpdateOperation(TextRange.from(offset, 0), text));
  }

  protected final void insertAfterLine(int lineNumber, @NotNull String text) {
    final Substring line = myOriginalDocString.getLines().get(lineNumber);
    insert(line.getEndOffset(), "\n" + text);
  }

  protected final void replace(int startOffset, int endOffset, @NotNull String text) {
    replace(new TextRange(startOffset, endOffset), text);
  }

  protected final void replace(@NotNull TextRange range, @NotNull String text) {
    myUpdates.add(new UpdateOperation(range, text));
  }

  protected abstract void scheduleUpdates();

  @NotNull
  public final String getDocStringText() {
    scheduleUpdates();
    // if several updates insert in one place (e.g. new field), insert them in backward order
    Collections.reverse(myUpdates);
    Collections.sort(myUpdates);
    for (int i = myUpdates.size() - 1; i >= 0; i--) {
      final UpdateOperation update = myUpdates.get(i);
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

  @NotNull
  public T getOriginalDocString() {
    return myOriginalDocString;
  }

  protected static class AddParameter {
    @NotNull final String name;
    @Nullable final String type;

    public AddParameter(@NotNull String name, @Nullable String type) {
      this.name = name;
      this.type = type;
    }
  }

  protected static class AddReturnType {
    @Nullable final String name;
    @NotNull final String type;

    public AddReturnType(@Nullable String name, @NotNull String type) {
      this.name = name;
      this.type = type;
    }
  }

  protected static class AddException {
    @NotNull final String type;

    public AddException(@NotNull String type) {
      this.type = type;
    }
  }

  private static class UpdateOperation implements Comparable<UpdateOperation> {
    @NotNull final TextRange range;
    @NotNull final String text;

    public UpdateOperation(@NotNull TextRange range, @NotNull String newText) {
      this.range = range;
      this.text = newText;
    }

    @Override
    public int compareTo(UpdateOperation o) {
      return range.getStartOffset() - o.range.getStartOffset();
    }
  }
}
