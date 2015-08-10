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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 */
public class GoogleCodeStyleDocString extends SectionBasedDocString {
  public static final Pattern SECTION_HEADER_RE = Pattern.compile("\\s*(\\w+):\\s*", Pattern.MULTILINE);
  private static final Pattern FIELD_NAME_AND_TYPE_RE = Pattern.compile("\\s*(.+?)\\s*\\(\\s*(.+?)\\s*\\)\\s*");

  public GoogleCodeStyleDocString(@NotNull String text) {
    super(text);
  }

  /**
   * <h3>Examples</h3>
   * <ol>
   * <li>
   * mayHaveType=true, preferType=false
   * <pre><code>
   * Attributes:
   *     arg1 (int): description; `(int)` is optional
   * </code></pre>
   * </li>
   * <li>
   * mayHaveType=true, preferType=true
   * <pre><code>
   * Returns:
   *     code (int): description; `(int)` is optional
   * </code></pre>
   * </li>
   * <li>
   * mayHaveType=false, preferType=false
   * <pre><code>
   * Methods:
   *     my_method() : description
   * </code></pre>
   * </li>
   * <li>
   * mayHaveType=false, preferType=true
   * <pre><code>
   * Raises:
   *     Exception : description
   * </code></pre>
   * </li>
   * </li>
   * </ol>
   */
  @Override
  protected Pair<SectionField, Integer> parseSectionField(int lineNum,
                                                          int sectionIndent,
                                                          boolean mayHaveType,
                                                          boolean preferType) {
    final Substring line = getLine(lineNum);
    Substring name, type = null, description;
    final List<Substring> colonSeparatedParts = splitByFirstColon(line);
    assert colonSeparatedParts.size() <= 2;
    if (colonSeparatedParts.size() < 2) {
      return Pair.create(null, lineNum);
    }
    final Substring textBeforeColon = colonSeparatedParts.get(0);
    name = textBeforeColon.trim();
    if (mayHaveType) {
      final Matcher matcher = FIELD_NAME_AND_TYPE_RE.matcher(textBeforeColon);
      if (matcher.matches()) {
        name = Substring.fromMatcherGroup(textBeforeColon, matcher, 1).trim();
        type = Substring.fromMatcherGroup(textBeforeColon, matcher, 2).trim();
      }
    }

    if (preferType && type == null) {
      type = name;
      name = null;
    }
    description = colonSeparatedParts.get(1);
    // parse line with indentation at least one space greater than indentation of the field
    final Pair<List<Substring>, Integer> pair = parseIndentedBlock(lineNum + 1, getIndent(line), sectionIndent);
    final List<Substring> nestedBlock = pair.getFirst();
    if (!nestedBlock.isEmpty()) {
      //noinspection ConstantConditions
      description = mergeSubstrings(description, ContainerUtil.getLastItem(nestedBlock));
    }
    description = description.trim();
    return Pair.create(new SectionField(name, type, description), pair.getSecond());
  }


  @NotNull
  @Override
  protected Pair<String, Integer> parseSectionHeader(int lineNum) {
    final Matcher matcher = SECTION_HEADER_RE.matcher(getLine(lineNum));
    if (matcher.matches()) {
      return Pair.create(matcher.group(1).trim(), lineNum + 1);
    }
    return Pair.create(null, lineNum);
  }
}
