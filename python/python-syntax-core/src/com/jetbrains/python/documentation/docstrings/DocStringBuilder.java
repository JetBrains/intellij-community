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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class DocStringBuilder<This extends DocStringBuilder> {
  private final List<String> myLines;
  
  public DocStringBuilder() {
    myLines = new ArrayList<>();
  }

  @NotNull
  public This addLine(@NotNull String line) {
    return addLine(line, myLines.size());
  }

  @NotNull
  public This addLine(@NotNull String line, int index) {
    myLines.add(index, line);
    //noinspection unchecked
    return (This)this;
  }

  @NotNull
  public This addEmptyLine() {
    return addLine("", myLines.size());
  }

  @NotNull
  public This addEmptyLine(int index) {
    return addLine("", index);
  }

  @NotNull
  public List<String> getLines() {
    return Collections.unmodifiableList(myLines);
  }

  @NotNull
  public String buildContent(int indent, boolean indentFirst) {
    return buildContent(StringUtil.repeatSymbol(' ', indent), indentFirst);
  }

  @NotNull
  public String buildContent(@NotNull String indentation, boolean indentFirst) {
    final StringBuilder result = new StringBuilder();
    boolean first = true;
    for (String line : myLines) {
      if (!first) {
        result.append('\n');
      }
      // Do not add indentation for empty lines
      if (!StringUtil.isEmptyOrSpaces(line)) {
        if (!first || indentFirst) {
          result.append(indentation);
        }
        result.append(line);
      }
      first = false;
    }
    return result.toString();
  }
}
