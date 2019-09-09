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

  /**
   * Finds line number of the <em>line next after the end of indented block</em>, i.e. if returned value equals startLine,
   * it means that no block with requested indentation exist. Block can contain intermediate empty lines or lines that consists 
   * solely of spaces: their indentation is ignored, but it always ends with non-empty line. Non-empty lines in a block must
   * have indentation greater than indentThreshold and for them {@link #isBlockEnd(int)} must return false.
   * 
   * @param indentThreshold indentation size of non-empty line that will break block once reached, i.e. minimum possible indentation 
   *                        inside a block is {@code indentThreshold + 1}
   */
  public int consumeIndentedBlock(int startLine, int indentThreshold) {
    int blockEnd = startLine - 1;
    int lineNum = startLine;
    while (lineNum < getLineCount() && !isBlockEnd(lineNum)) {
      if (!isEmpty(lineNum)) {
        if (getLineIndentSize(lineNum) > indentThreshold) {
          blockEnd = lineNum;
        }
        else {
          break;
        }
      }
      lineNum++;
    }
    return blockEnd + 1;
  }

  public int consumeEmptyLines(int lineNum) {
    while (lineNum < getLineCount() && isEmpty(lineNum)) {
      lineNum++;
    }
    return lineNum;
  }

  protected abstract boolean isBlockEnd(int lineNum);
}
