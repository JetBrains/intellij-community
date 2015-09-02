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

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 * @see <a href="https://github.com/numpy/numpy/blob/master/doc/HOWTO_DOCUMENT.rst.txt">A Guide to NumPy/SciPy Documentation</a>
 * @see <a href="http://sphinxcontrib-napoleon.readthedocs.org/en/latest/example_numpy.html#example-numpy">Napoleon: Example NumPy Style Python Docstrings</a>
 */
public class NumpyDocString extends SectionBasedDocString {
  private static final Pattern SIGNATURE = Pattern.compile("^[ \t]*([\\w., ]+=)?[ \t]*[\\w\\.]+\\(.*\\)[ \t]*$", Pattern.MULTILINE);
  public static final Pattern SECTION_HEADER = Pattern.compile("^[ \t]*[-=]{2,}[ \t]*$", Pattern.MULTILINE);

  private Substring mySignature;
  public NumpyDocString(@NotNull Substring text) {
    super(text);
  }

  @Override
  protected int parseHeader(int startLine) {
    final int nextNonEmptyLineNum = skipEmptyLines(startLine);
    final Substring line = getLineOrNull(nextNonEmptyLineNum);
    if (line != null && SIGNATURE.matcher(line).matches()) {
      mySignature = line.trim();
      return nextNonEmptyLineNum + 1;
    }
    return nextNonEmptyLineNum;
  }

  @NotNull
  @Override
  protected Pair<Substring, Integer> parseSectionHeader(int lineNum) {
    @NonNls final String title = getLine(lineNum).trim().toString();
    if (SECTION_NAMES.contains(title.toLowerCase())) {
      final Substring nextLine = getLineOrNull(lineNum + 1);
      if (nextLine != null && SECTION_HEADER.matcher(nextLine).matches()) {
        return Pair.create(getLine(lineNum).trim(), lineNum + 2);
      }
    }
    return Pair.create(null, lineNum);
  }

  @Override
  protected Pair<SectionField, Integer> parseSectionField(int lineNum,
                                                          int sectionIndent,
                                                          boolean mayHaveType,
                                                          boolean preferType) {
    final Substring line = getLine(lineNum);
    Substring name, type = null, description = null;
    if (mayHaveType) {
      final List<Substring> colonSeparatedParts = splitByFirstColon(line);
      name = colonSeparatedParts.get(0).trim();
      if (colonSeparatedParts.size() == 2) {
        type = colonSeparatedParts.get(1).trim();
      }
    }
    else {
      name = line.trim();
    }
    if (preferType && type == null) {
      type = name;
      name = null;
    }
    final Pair<List<Substring>, Integer> parsedDescription = parseIndentedBlock(lineNum + 1, getLineIndentSize(lineNum), sectionIndent);
    final List<Substring> descriptionLines = parsedDescription.getFirst();
    if (!descriptionLines.isEmpty()) {
      description = descriptionLines.get(0).union(descriptionLines.get(descriptionLines.size() - 1));
    }
    return Pair.create(new SectionField(name, type, description != null ? description.trim() : null), parsedDescription.getSecond());
  }

  @NotNull
  public String getSignature() {
    return mySignature != null ? mySignature.toString() : "";
  }

  @Override
  protected boolean isBlockEnd(int lineNum) {
    return super.isBlockEnd(lineNum) || (isEmpty(lineNum) && isEmptyOrDoesNotExist(lineNum + 1));
  }
}
