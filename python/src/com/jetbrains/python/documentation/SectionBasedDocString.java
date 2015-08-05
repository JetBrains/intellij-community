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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Common base class for docstring styles supported by Napoleon Sphinx extension.
 *
 * @author Mikhail Golubev
 * @see <a href="http://sphinxcontrib-napoleon.readthedocs.org/en/latest/index.html">Napoleon</a>
 */
public abstract class SectionBasedDocString implements StructuredDocString {
  private static final Map<String, String> SECTION_ALIASES =
    ImmutableMap.<String, String>builder()
                .put("arguments", "parameters")
                .put("args", "parameters")
                .put("parameters", "parameters")
                .put("keyword args", "keyword arguments")
                .put("keyword arguments", "keyword arguments")
                .put("other parameters", "other parameters")
                .put("attributes", "attributes")
                .put("methods", "methods")
                .put("note", "notes")
                .put("notes", "notes")
                .put("example", "examples")
                .put("examples", "examples")
                .put("return", "returns")
                .put("returns", "returns")
                .put("yield", "yields")
                .put("yields", "yields")
                .put("raises", "raises")
                .put("references", "references")
                .put("see also", "see also")
                .put("warning", "warnings")
                .put("warns", "warnings")
                .put("warnings", "warnings")
                .build();

  private static ImmutableSet<String> SECTIONS_WITH_NAME_AND_TYPE = ImmutableSet.of("attributes", "methods",
                                                                                    "parameters", "keyword arguments", "other parameters");
  private static ImmutableSet<String> SECTIONS_WITH_TYPE = ImmutableSet.of("returns", "raises", "yields");

  protected final List<Substring> myLines;

  private final List<Substring> myOtherContent = new ArrayList<Substring>();
  private final List<Section> mySections = new ArrayList<Section>();
  private final String mySummary;

  protected SectionBasedDocString(@NotNull String text) {
    myLines = new Substring(text).splitLines();
    String summary = "";
    int lineNum = 0;
    while (lineNum < myLines.size()) {
      Pair<Section, Integer> result = parseSection(lineNum);
      if (result.getFirst() != null) {
        mySections.add(result.getFirst());
        lineNum = result.getSecond();
      }
      else {
        if (lineNum == 0 && isEmptyOrDoesNotExist(lineNum + 1)) {
          summary = getLine(0).trim().toString();
        }
        else {
          myOtherContent.add(getLine(lineNum));
        }
        lineNum++;
      }
    }
    mySummary = summary;
  }

  @NotNull
  protected Pair<Section, Integer> parseSection(int sectionStartLine) {
    final Pair<String, Integer> pair = parseSectionHeader(sectionStartLine);
    final String title = normalizeSectionTitle(pair.getFirst());
    if (title == null) {
      return Pair.create(null, sectionStartLine);
    }
    int lineNum = skipEmptyLines(pair.getSecond());
    final List<SectionField> fields = new ArrayList<SectionField>();
    final int sectionIndent = getLineIndent(sectionStartLine);
    while (!isSectionBreak(lineNum, sectionIndent)) {
      final Pair<SectionField, Integer> result = parseField(lineNum, title, sectionIndent);
      if (result.getFirst() == null) {
        break;
      }
      fields.add(result.getFirst());
      lineNum = skipEmptyLines(result.getSecond());
    }
    return Pair.create(new Section(title, fields), lineNum);
  }

  @NotNull
  protected Pair<SectionField, Integer> parseField(int lineNum, @NotNull String sectionTitle, int sectionIndent) {
    if (SECTIONS_WITH_NAME_AND_TYPE.contains(sectionTitle)) {
      return parseFieldWithNameAndOptionalType(lineNum, sectionIndent);
    }
    if (SECTIONS_WITH_TYPE.contains(sectionTitle)) {
      return parseFieldWithType(lineNum, sectionIndent);
    }
    return parseGeneralField(lineNum, sectionIndent);
  }

  @NotNull
  protected Pair<SectionField, Integer> parseGeneralField(int lineNum, int sectionIndent) {
    final Pair<List<Substring>, Integer> pair = parseIndentedBlock(lineNum, sectionIndent, sectionIndent);
    final Substring firstLine = ContainerUtil.getFirstItem(pair.getFirst());
    final Substring lastLine = ContainerUtil.getLastItem(pair.getFirst());
    if (firstLine != null && lastLine != null) {
      final Substring mergedSubstring = new Substring(firstLine.getSuperString(), firstLine.getStartOffset(), lastLine.getEndOffset());
      return Pair.create(new SectionField(null, null, mergedSubstring), pair.getSecond());
    }
    return Pair.create(null, pair.getSecond());
  }

  @NotNull
  protected abstract Pair<SectionField, Integer> parseFieldWithType(int lineNum, int sectionIndent);

  @NotNull
  protected abstract Pair<SectionField, Integer> parseFieldWithNameAndOptionalType(int lineNum, int sectionIndent);

  @NotNull
  protected abstract Pair<String, Integer> parseSectionHeader(int lineNum);

  protected int getLineIndent(int lineNum) {
    final Substring line = getLine(lineNum);
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isSpaceChar(line.charAt(i))) {
        return i;
      }
    }
    return 0;
  }

  private int skipEmptyLines(int lineNum) {
    while (lineNum < myLines.size() && isEmpty(lineNum)) {
      lineNum++;
    }
    return lineNum;
  }

  @Nullable
  protected String normalizeSectionTitle(@Nullable @NonNls String title) {
    return title == null ? null : SECTION_ALIASES.get(title.toLowerCase());
  }

  private boolean isEmptyOrDoesNotExist(int lineNum) {
    return lineNum >= myLines.size() - 1 || isEmpty(lineNum);
  }

  private boolean isEmpty(int lineNum) {
    return StringUtil.isEmptyOrSpaces(getLine(lineNum));
  }

  private boolean isSectionStart(int lineNum) {
    final Pair<String, Integer> pair = parseSectionHeader(lineNum);
    return pair.getFirst() != null;
  }

  private boolean isSectionBreak(int lineNum, int curSectionIndent) {
    return lineNum >= myLines.size() ||
           isSectionStart(lineNum) ||
           (!isEmpty(lineNum) && getLineIndent(lineNum) <= curSectionIndent);
  }

  /**
   * Consumes all lines that are indented more than {@code blockIndent} and don't contain start of a new section.
   * Trailing empty lines (e.g. due to indentation of closing triple quotes) are omitted in result.
   */
  @NotNull
  protected Pair<List<Substring>, Integer> parseIndentedBlock(int lineNum, int blockIndent, int sectionIndent) {
    final List<Substring> result = new ArrayList<Substring>();
    int lastNonEmpty = lineNum - 1;
    while (!isSectionBreak(lineNum, sectionIndent)) {
      if (getLineIndent(lineNum) > blockIndent) {
        // copy all lines after the last non empty including the current one
        for (int i = lastNonEmpty + 1; i <= lineNum; i++) {
          result.add(getLine(lineNum));
        }
        lastNonEmpty = lineNum;
      }
      else if (!isEmpty(lineNum)) {
        break;
      }
      lineNum++;
    }
    return Pair.create(result, lineNum);
  }

  @NotNull
  protected Substring getLine(int indent) {
    return myLines.get(indent);
  }

  @VisibleForTesting
  public List<Section> getSections() {
    return Collections.unmodifiableList(mySections);
  }

  @NotNull
  @Override
  public String createParameterType(@NotNull String name, @NotNull String type) {
    return null;
  }

  @Override
  public String getSummary() {
    return mySummary.toString();
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public List<String> getParameters() {
    return null;
  }

  @Override
  public List<Substring> getParameterSubstrings() {
    return null;
  }

  @Nullable
  @Override
  public String getParamType(@Nullable String paramName) {
    return null;
  }

  @Nullable
  @Override
  public Substring getParamTypeSubstring(@Nullable String paramName) {
    return null;
  }

  @Nullable
  @Override
  public String getParamDescription(@Nullable String paramName) {
    return null;
  }

  @Override
  public List<String> getKeywordArguments() {
    return null;
  }

  @Override
  public List<Substring> getKeywordArgumentSubstrings() {
    return null;
  }

  @Nullable
  @Override
  public String getKeywordArgumentDescription(@Nullable String paramName) {
    return null;
  }

  @Nullable
  @Override
  public String getReturnType() {
    return null;
  }

  @Nullable
  @Override
  public Substring getReturnTypeSubstring() {
    return null;
  }

  @Nullable
  @Override
  public String getReturnDescription() {
    return null;
  }

  @Override
  public List<String> getRaisedExceptions() {
    return null;
  }

  @Nullable
  @Override
  public String getRaisedExceptionDescription(@Nullable String exceptionName) {
    return null;
  }

  @Nullable
  @Override
  public String getAttributeDescription() {
    return null;
  }

  @Nullable
  @Override
  public Substring getTagValue(String... tagNames) {
    return null;
  }

  @Nullable
  @Override
  public Substring getTagValue(String tagName, @NotNull String argName) {
    return null;
  }

  @Nullable
  @Override
  public Substring getTagValue(String[] tagNames, @NotNull String argName) {
    return null;
  }

  @Override
  public List<Substring> getTagArguments(String... tagNames) {
    return null;
  }

  @Nullable
  @Override
  public Substring getParamByNameAndKind(@NotNull String name, String kind) {
    return null;
  }

  @Override
  public List<String> getAdditionalTags() {
    return null;
  }

  public static class Section {
    private final String myTitle;
    private final List<SectionField> myFields;

    public Section(@NotNull String title, @NotNull List<SectionField> fields) {
      myTitle = title;
      myFields = new ArrayList<SectionField>(fields);
    }

    @NotNull
    public String getTitle() {
      return myTitle;
    }

    @NotNull
    public List<SectionField> getFields() {
      return Collections.unmodifiableList(myFields);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Section section = (Section)o;

      if (!myTitle.equals(section.myTitle)) return false;
      if (!myFields.equals(section.myFields)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myTitle.hashCode();
      result = 31 * result + myFields.hashCode();
      return result;
    }
  }

  public static class SectionField {
    private final Substring myName;
    private final Substring myType;
    private final Substring myDescription;

    public SectionField(@Nullable Substring name, @Nullable Substring type, @Nullable Substring description) {
      myName = name;
      myType = type;
      myDescription = description;
    }

    @NotNull
    public String getName() {
      return myName == null ? "" : myName.toString();
    }

    @Nullable
    public Substring getNameAsSubstring() {
      return myName;
    }

    @NotNull
    public String getType() {
      return myType == null ? "" : myType.toString();
    }

    @Nullable 
    public Substring getTypeAsSubstring() {
      return myType;
    }

    @NotNull
    public String getDescription() {
      return myDescription == null ? "" : myDescription.toString();
    }

    @Nullable
    public Substring getDescriptionAsSubstring() {
      return myDescription;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SectionField field = (SectionField)o;

      if (myName != null ? !myName.equals(field.myName) : field.myName != null) return false;
      if (myType != null ? !myType.equals(field.myType) : field.myType != null) return false;
      if (myDescription != null ? !myDescription.equals(field.myDescription) : field.myDescription != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName != null ? myName.hashCode() : 0;
      result = 31 * result + (myType != null ? myType.hashCode() : 0);
      result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
      return result;
    }
  }

  // MethodsSection
  // AttributesSection
  // YieldsSection
  // RaisesSection
  // ReturnsSection
}
