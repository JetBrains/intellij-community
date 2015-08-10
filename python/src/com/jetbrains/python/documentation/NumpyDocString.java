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
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 */
public class NumpyDocString extends SectionBasedDocString {
  private static final Pattern SIGNATURE = Pattern.compile("^([\\w., ]+=)?\\s*[\\w\\.]+\\(.*\\)$");
  private static final Pattern SECTION_HEADER = Pattern.compile("^[-=]+");

  private Substring mySignature = null;
  public NumpyDocString(@NotNull String text) {
    super(text);
  }

  @Override
  protected int parseHeader(int startLine) {
    final int nextNonEmptyLineNum = skipEmptyLines(startLine);
    final Substring line = getLineOrNull(nextNonEmptyLineNum);
    if (line != null && SIGNATURE.matcher(line).matches()) {
      mySignature = line.trim();
    }
    return super.parseHeader(startLine);
  }

  @NotNull
  @Override
  protected Pair<String, Integer> parseSectionHeader(int lineNum) {
    final Substring nextLine = getLineOrNull(lineNum + 1);
    if (nextLine != null && SECTION_HEADER.matcher(nextLine).matches()) {
      return Pair.create(getLine(lineNum).trim().toString(), lineNum + 2);
    }
    return Pair.create(null, lineNum);
  }

  @Override
  protected Pair<SectionField, Integer> parseField(int lineNum,
                                                   int sectionIndent,
                                                   boolean mayHaveType,
                                                   boolean preferType) {
    return null;
  }

  @NotNull
  public String getSignature() {
    return mySignature != null ? mySignature.toString() : "";
  }
}
