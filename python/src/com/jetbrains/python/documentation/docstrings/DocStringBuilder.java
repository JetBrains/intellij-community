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
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
  public DocStringBuilder addSummary(@NotNull String summary) {
    addLine(summary);
    addLine("");
    return this;
  }

  @NotNull
  public DocStringBuilder startParameterSection() {
    return this;
  }
  @NotNull
  public abstract DocStringBuilder addParameter(@NotNull String name, @Nullable String type);

  public abstract DocStringBuilder addParameterType(@NotNull String name, @NotNull String type);

  @NotNull
  public DocStringBuilder startReturnValueSection() {
    return this;
  }
  @NotNull
  public abstract DocStringBuilder addReturnValue(@Nullable String name, @NotNull String type);

  @NotNull
  protected DocStringBuilder addLine(@NotNull String line) {
    myLines.add(line);
    return this;
  }

  @NotNull
  protected DocStringBuilder addLine(@NotNull @PrintFormat String format, @NotNull Object... args) {
    myLines.add(String.format(format, args));
    return this;
  }

  @NotNull
  public String buildContent(int indent, boolean indentFirst) {
    final StringBuilder result = new StringBuilder();
    if (!indentFirst && !myLines.isEmpty()) {
      result.append(myLines.get(0)).append('\n');
    }
    boolean first = true;
    String indentation = StringUtil.repeat(" ", indent);
    for (int i = indentFirst ? 0 : 1; i < myLines.size(); i++) {
      if (first) {
        first = false;
      }
      else {
        result.append('\n');
      }
      result.append(indentation).append(myLines.get(i));
    }
    return result.toString();
  }
}
