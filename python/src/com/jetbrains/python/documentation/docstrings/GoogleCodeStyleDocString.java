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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mikhail Golubev
 * @see <a href="http://sphinxcontrib-napoleon.readthedocs.org/en/latest/example_google.html#example-google">Napoleon: Example Google Style Python Docstrings</a>
 * @see <a href="http://google-styleguide.googlecode.com/svn/trunk/pyguide.html?showone=Comments#Comments">Google Python Style: Docstrings</a>
 */
public class GoogleCodeStyleDocString extends SectionBasedDocString {
  public static final Pattern SECTION_HEADER = Pattern.compile("^[ \t]*([\\w \t]+):[ \t]*$", Pattern.MULTILINE);
  private static final Pattern FIELD_NAME_AND_TYPE = Pattern.compile("^[ \t]*(.+?)[ \t]*\\([ \t]*(.*?)[ \t]*\\)?[ \t]*$", Pattern.MULTILINE);

  public static final List<String> PREFERRED_SECTION_HEADERS = ImmutableList.of("Args",
                                                                                "Keyword Args",
                                                                                "Returns",
                                                                                "Yields",
                                                                                "Raises",
                                                                                "Attributes",
                                                                                "See Also",
                                                                                "Methods",
                                                                                "References",
                                                                                "Examples",
                                                                                "Notes",
                                                                                "Warnings");

  public GoogleCodeStyleDocString(@NotNull Substring text) {
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
    Substring name, type = null;
    // Napoleon requires that each parameter line contains a colon - we don't because
    // we need to parse and complete parameter names before colon is typed
    final List<Substring> colonSeparatedParts = splitByFirstColon(line);
    assert colonSeparatedParts.size() <= 2;
    final Substring textBeforeColon = colonSeparatedParts.get(0);
    name = textBeforeColon.trim();
    if (mayHaveType) {
      final Matcher matcher = FIELD_NAME_AND_TYPE.matcher(textBeforeColon);
      if (matcher.matches()) {
        name = textBeforeColon.getMatcherGroup(matcher, 1).trim();
        type = textBeforeColon.getMatcherGroup(matcher, 2).trim();
      }
    }

    if (preferType && type == null) {
      type = name;
      name = null;
    }
    if (name != null) {
      name = cleanUpName(name);
    }
    if (name != null ? !isValidName(name.toString()) : !isValidType(type.toString())) {
      return Pair.create(null, lineNum);
    }
    final Pair<List<Substring>, Integer> pair;
    if (colonSeparatedParts.size() == 2) {
      Substring description = colonSeparatedParts.get(1);
      // parse line with indentation at least one space greater than indentation of the field
      pair = parseIndentedBlock(lineNum + 1, getLineIndentSize(lineNum));
      final List<Substring> nestedBlock = pair.getFirst();
      if (!nestedBlock.isEmpty()) {
        //noinspection ConstantConditions
        description = description.union(ContainerUtil.getLastItem(nestedBlock));
      }
      description = description.trim();
      return Pair.create(new SectionField(name, type, description), pair.getSecond());
    }
    return Pair.create(new SectionField(name, type, null), lineNum + 1);
  }


  @NotNull
  @Override
  protected Pair<Substring, Integer> parseSectionHeader(int lineNum) {
    final Substring line = getLine(lineNum);
    final Matcher matcher = SECTION_HEADER.matcher(line);
    if (matcher.matches()) {
      final Substring title = line.getMatcherGroup(matcher, 1).trim();
      if (isValidSectionTitle(title.toString())) {
        return Pair.create(title, lineNum + 1);
      }
    }
    return Pair.create(null, lineNum);
  }
}
