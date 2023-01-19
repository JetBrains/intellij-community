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
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  private static final ImmutableMap<String, FieldType> ourSectionFieldMapping =
    ImmutableMap.<String, FieldType>builder()
      .put(RETURNS_SECTION, FieldType.OPTIONAL_TYPE)
      .put(YIELDS_SECTION, FieldType.OPTIONAL_TYPE)
      .put(RAISES_SECTION, FieldType.ONLY_TYPE)
      .put(METHODS_SECTION, FieldType.ONLY_NAME)
      .put(KEYWORD_ARGUMENTS_SECTION, FieldType.NAME_WITH_OPTIONAL_TYPE)
      .put(PARAMETERS_SECTION, FieldType.NAME_WITH_OPTIONAL_TYPE)
      .put(ATTRIBUTES_SECTION, FieldType.NAME_WITH_OPTIONAL_TYPE)
      .put(OTHER_PARAMETERS_SECTION, FieldType.NAME_WITH_OPTIONAL_TYPE)
      .build();

  public GoogleCodeStyleDocString(@NotNull Substring text) {
    super(text);
  }

  @Nullable
  @Override
  protected FieldType getFieldType(@NotNull String title) {
    return ourSectionFieldMapping.get(title);
  }

  /**
   * <h3>Examples</h3>
   * <ol>
   * <li>
   * canHaveBothNameAndType=true, preferType=false
   * <pre>{@code
   * Attributes:
   *     arg1 (int): description; `(int)` is optional
   * }</pre>
   * </li>
   * <li>
   * canHaveBothNameAndType=true, preferType=true
   * <pre>{@code
   * Returns:
   *     code (int): description; `(int)` is optional
   * }</pre>
   * </li>
   * <li>
   * canHaveBothNameAndType=false, preferType=false
   * <pre>{@code
   * Methods:
   *     my_method() : description
   * }</pre>
   * </li>
   * <li>
   * canHaveBothNameAndType=false, preferType=true
   * <pre>{@code
   * Raises:
   *     Exception : description
   * }</pre>
   * </li>
   * </li>
   * </ol>
   */
  @Override
  protected Pair<SectionField, Integer> parseSectionField(int lineNum, int sectionIndent, @NotNull FieldType fieldType) {
    final Substring line = getLine(lineNum);
    Substring name = null;
    Substring type = null;
    Substring description;
    // Napoleon requires that each parameter line contains a colon - we don't because
    // we need to parse and complete parameter names before colon is typed
    final List<Substring> colonSeparatedParts = splitByFirstColon(line);
    assert colonSeparatedParts.size() <= 2;
    final Substring textBeforeColon = colonSeparatedParts.get(0);

    // In cases like the following:
    //
    // Returns:
    //   Foo
    //
    // Napoleon treats "Foo" as the return value description, not type, since there is no subsequent colon.
    if (colonSeparatedParts.size() == 2 || !fieldType.canHaveOnlyDescription) {
      description = colonSeparatedParts.size() == 2 ? colonSeparatedParts.get(1) : null;
      name = textBeforeColon.trim();
      if (fieldType.canHaveBothNameAndType) {
        final Matcher matcher = FIELD_NAME_AND_TYPE.matcher(textBeforeColon);
        if (matcher.matches()) {
          name = textBeforeColon.getMatcherGroup(matcher, 1).trim();
          type = textBeforeColon.getMatcherGroup(matcher, 2).trim();
        }
      }

      if (fieldType.preferType && type == null) {
        type = name;
        name = null;
      }
      if (name != null) {
        name = cleanUpName(name);
      }
      if (name != null ? !isValidName(name.toString()) : !isValidType(type.toString())) {
        return Pair.create(null, lineNum);
      }
    }
    else {
      description = textBeforeColon;
    }

    if (description != null) {
      final Pair<List<Substring>, Integer> pair = parseFieldContinuation(lineNum, fieldType);
      final List<Substring> nestedBlock = pair.getFirst();
      if (!nestedBlock.isEmpty()) {
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
