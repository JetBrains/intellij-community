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
public abstract class DocStringBuilder {
  private final List<String> myLines;
  public DocStringBuilder() {
    myLines = new ArrayList<String>();
  }

  @NotNull
  public DocStringBuilder addLine(@NotNull String line) {
    myLines.add(line);
    return this;
  }

  @NotNull
  public DocStringBuilder addEmptyLine() {
    return addLine("");
  }

  @NotNull
  public List<String> getLines() {
    return Collections.unmodifiableList(myLines);
  }

  @NotNull
  public String buildContent(int indent, boolean indentFirst) {
    return buildContent(StringUtil.repeat(" ", indent), indentFirst);
  }

  @NotNull
  public String buildContent(@NotNull String indentation, boolean indentFirst) {
    final StringBuilder result = new StringBuilder();
    if (!indentFirst && !myLines.isEmpty()) {
      if (!StringUtil.isEmptyOrSpaces(myLines.get(0))) {
        result.append(myLines.get(0));
      }
      result.append('\n');
    }
    boolean first = true;
    for (int i = indentFirst ? 0 : 1; i < myLines.size(); i++) {
      if (first) {
        first = false;
      }
      else {
        result.append('\n');
      }
      final String line = myLines.get(i);
      if (!StringUtil.isEmptyOrSpaces(line)) {
        result.append(indentation).append(line);
      }
    }
    return result.toString();
  }
}
