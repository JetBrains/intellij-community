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
package com.jetbrains.python.documentation;

import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public abstract class DocStringLineParser {
  protected final List<Substring> myLines;
  protected final Substring myDocStringContent;

  protected DocStringLineParser(@NotNull Substring content) {
    myDocStringContent = content;
    myLines = Collections.unmodifiableList(content.splitLines());
  }

  public int getLineIndentSize(int lineNum) {
    return PyIndentUtil.getLineIndentSize(getLine(lineNum));
  }

  @NotNull
  public String getLineIndent(int lineNum) {
    return PyIndentUtil.getLineIndent(getLine(lineNum)).toString();
  }

  public boolean isEmptyOrDoesNotExist(int lineNum) {
    return lineNum < 0 || lineNum >= myLines.size() || isEmpty(lineNum);
  }

  public boolean isEmpty(int lineNum) {
    return StringUtil.isEmptyOrSpaces(getLine(lineNum));
  }

  @NotNull
  public Substring getLine(int lineNum) {
    return myLines.get(lineNum);
  }

  public int getLineByOffset(int offset) {
    return StringUtil.countNewLines(myDocStringContent.getSuperString().subSequence(0, offset));
  }

  @Nullable
  public Substring getLineOrNull(int lineNum) {
    return lineNum >= 0 && lineNum < myLines.size() ? myLines.get(lineNum) : null;
  }

  public int getLineCount() {
    return myLines.size();
  }

  @NotNull
  public List<Substring> getLines() {
    return myLines;
  }

  @NotNull
  public Substring getDocStringContent() {
    return myDocStringContent;
  }
}
