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
  private static final Pattern SECTION_HEADER_RE = Pattern.compile("^\\s*(.+?):\\s*$");
  private static final Pattern FIELD_NAME_AND_TYPE_RE = Pattern.compile("\\s*(.+?)\\s*\\(\\s*(.+?)\\s*\\)\\s*");

  public GoogleCodeStyleDocString(@NotNull String text) {
    super(text);
  }

  @NotNull
  @Override
  protected Pair<SectionField, Integer> parseFieldWithType(int lineNum, int sectionIndent) {
    return parseField(lineNum, sectionIndent, true);
  }

  @NotNull
  @Override
  protected Pair<SectionField, Integer> parseFieldWithNameAndOptionalType(int lineNum, int sectionIndent) {
    return parseField(lineNum, sectionIndent, false);
  }

  /**
   * <h3>Example</h3>
   * <pre><code>
   * Attributes:
   *  arg1 (int): field with name and optional type before description
   *  
   * Raises:
   *  RuntimeException: field with only type before description
   * </code></pre>                       
   * 
   * @param typeBeforeColon according to Google Code Style there can be either type or name and type in parenthesis before colon
   */
  @NotNull
  private Pair<SectionField, Integer> parseField(int lineNum, int sectionIndent, boolean typeBeforeColon) {
    Substring name = null, type = null, description;
    final List<Substring> parts = getLine(lineNum).split(":", 1);
    assert parts.size() <= 2;
    if (parts.size() < 2) {
      return Pair.create(null, lineNum);
    }
    final Substring textBeforeColon = parts.get(0);
    if (typeBeforeColon) {
      type = textBeforeColon.trim();
    }
    else {
      // TODO skip references in types like Napoleon does
      final Matcher matcher = FIELD_NAME_AND_TYPE_RE.matcher(textBeforeColon);
      if (matcher.matches()) {
        name = Substring.fromMatcherGroup(textBeforeColon, matcher, 1).trim();
        type = Substring.fromMatcherGroup(textBeforeColon, matcher, 2).trim();
      }
      else {
        name = textBeforeColon.trim();
      }
    }
    description = parts.get(1);
    final Pair<List<Substring>, Integer> pair = parseIndentedBlock(lineNum + 1, getLineIndent(lineNum), sectionIndent);
    final List<Substring> nestedBlock = pair.getFirst();
    if (!nestedBlock.isEmpty()) {
      //noinspection ConstantConditions
      description = description.getSmallestInclusiveSubstring(ContainerUtil.getLastItem(nestedBlock));
    }
    assert description != null;
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
