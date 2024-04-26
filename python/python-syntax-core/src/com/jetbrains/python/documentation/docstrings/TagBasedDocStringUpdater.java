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

import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class TagBasedDocStringUpdater extends DocStringUpdater<TagBasedDocString>{

  private final String myTagPrefix;

  public TagBasedDocStringUpdater(@NotNull TagBasedDocString docString, @NotNull String tagPrefix, @NotNull String minContentIndent) {
    super(docString, minContentIndent);
    myTagPrefix = tagPrefix;
  }

  @NotNull
  private TagBasedDocStringBuilder createBuilder() {
    return new TagBasedDocStringBuilder(myTagPrefix);
  }

  @Override
  public final void addParameter(@NotNull String name, @Nullable String type) {
    if (type != null) {
      insertTagLine(createBuilder().addParameterType(name, type));
    }
    else {
      insertTagLine(createBuilder().addParameterDescription(name, ""));
    }
  }

  @Override
  public final void addReturnValue(@Nullable String type) {
    if (type != null) {
      insertTagLine(createBuilder().addReturnValueType(type));
    }
    else {
      insertTagLine(createBuilder().addReturnValueDescription(""));
    }
  }

  @Override
  public void removeParameter(@NotNull String name) {
    final List<Substring> nameSubs = myOriginalDocString.getParameterSubstrings();
    for (Substring sub : nameSubs) {
      if (sub.toString().equals(name)) {
        final int startLine = sub.getStartLine();
        final int nextAfterBlock = myOriginalDocString.consumeIndentedBlock(startLine + 1, getLineIndentSize(startLine));
        removeLinesAndSpacesAfter(startLine, nextAfterBlock);
      }
    }
  }

  private void insertTagLine(@NotNull DocStringBuilder lineBuilder) {
    final int firstLineWithTag = findFirstLineWithTag();
    if (firstLineWithTag >= 0) {
      final String indent = getLineIndent(firstLineWithTag);
      insertBeforeLine(firstLineWithTag, lineBuilder.buildContent(indent, true));
      return;
    }
    final int lastNonEmptyLine = findLastNonEmptyLine();
    final String indent = getLineIndent(lastNonEmptyLine);
    insertAfterLine(lastNonEmptyLine, lineBuilder.buildContent(indent, true));
  }

  private int findFirstLineWithTag() {
    for (int i = 0; i < myOriginalDocString.getLineCount(); i++) {
      final Substring line = myOriginalDocString.getLine(i);
      if (line.trimLeft().startsWith(myTagPrefix)) {
        return i;
      }
    }
    return -1;
  }
}
