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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Common base class for docstring styles supported by Napoleon Sphinx extension.
 *
 * @author Mikhail Golubev
 * @see <a href="http://sphinxcontrib-napoleon.readthedocs.org/en/latest/index.html">Napoleon</a>
 */
public abstract class SectionBasedDocString implements StructuredDocString {
  
  /**
   * Frequently used section types
   */
  @NonNls private static final String NORMALIZED_RETURN_SECTION_NAME = "returns";
  @NonNls private static final String NORMALIZED_EXCEPTIONS_SECTION_NAME = "raises";
  @NonNls private static final String NORMALIZED_KEYWORD_ARGUMENTS_SECTION = "keyword arguments";
  @NonNls private static final String NORMALIZED_PARAMETERS_SECTION = "parameters";
  
  private static final Map<String, String> SECTION_ALIASES =
    ImmutableMap.<String, String>builder()
                .put("arguments", NORMALIZED_PARAMETERS_SECTION)
                .put("args", NORMALIZED_PARAMETERS_SECTION)
                .put("parameters", NORMALIZED_PARAMETERS_SECTION)
                .put("keyword args", NORMALIZED_KEYWORD_ARGUMENTS_SECTION)
                .put("keyword arguments", NORMALIZED_KEYWORD_ARGUMENTS_SECTION)
                .put("other parameters", "other parameters")
                .put("attributes", "attributes")
                .put("methods", "methods")
                .put("note", "notes")
                .put("notes", "notes")
                .put("example", "examples")
                .put("examples", "examples")
                .put("return", NORMALIZED_RETURN_SECTION_NAME)
                .put("returns", NORMALIZED_RETURN_SECTION_NAME)
                .put("yield", "yields")
                .put("yields", "yields")
                .put("raises", NORMALIZED_EXCEPTIONS_SECTION_NAME)
                .put("references", "references")
                .put("see also", "see also")
                .put("warning", "warnings")
                .put("warns", "warnings")
                .put("warnings", "warnings")
                .build();

  public static Set<String> SECTION_NAMES = SECTION_ALIASES.keySet();
  private static final ImmutableSet<String> SECTIONS_WITH_NAME_AND_TYPE =
    ImmutableSet.of("attributes", "methods", "parameters", "keyword arguments", "other parameters");
  private static final ImmutableSet<String> SECTIONS_WITH_TYPE = ImmutableSet.of("returns", "raises", "yields");

  protected final List<Substring> myLines;

  private final Substring mySummary;
  private final List<Section> mySections = new ArrayList<Section>();
  private final List<Substring> myOtherContent = new ArrayList<Substring>();

  protected SectionBasedDocString(@NotNull String text) {
    myLines = new Substring(text).splitLines();
    List<Substring> summary = Collections.emptyList();
    final int startLine = parseHeader(0);
    int lineNum = skipEmptyLines(startLine);
    while (lineNum < myLines.size()) {
      final Pair<Section, Integer> parsedSection = parseSection(lineNum);
      if (parsedSection.getFirst() != null) {
        mySections.add(parsedSection.getFirst());
        lineNum = parsedSection.getSecond();
      }
      else if (lineNum == startLine) {
        final Pair<List<Substring>, Integer> parsedSummary = parseSummary(lineNum);
        summary = parsedSummary.getFirst();
        lineNum = parsedSummary.getSecond();
      }
      else {
        myOtherContent.add(getLine(lineNum));
        lineNum++;
      }
      lineNum = skipEmptyLines(lineNum);
    }
    //noinspection ConstantConditions
    mySummary = summary.isEmpty() ? null : mergeSubstrings(summary.get(0), summary.get(summary.size() - 1)).trim();
  }

  @NotNull
  private Pair<List<Substring>, Integer> parseSummary(int lineNum) {
    final List<Substring> result = new ArrayList<Substring>();
    while (!isEmptyOrDoesNotExist(lineNum)) {
      result.add(getLine(lineNum));
      lineNum++;
    }
    return Pair.create(result, lineNum);
  }

  /**
   * Used to parse e.g. optional function signature at the beginning of NumPy-style docstring
   *
   * @return first line from which to star parsing remaining sections
   */
  protected int parseHeader(int startLine) {
    return startLine;
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
    final int sectionIndent = getIndent(getLine(sectionStartLine));
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
      //noinspection ConstantConditions
      return Pair.create(new SectionField(null, null, mergeSubstrings(firstLine, lastLine).trim()), pair.getSecond());
    }
    return Pair.create(null, pair.getSecond());
  }

  @NotNull
  protected abstract Pair<SectionField, Integer> parseFieldWithType(int lineNum, int sectionIndent);

  @NotNull
  protected abstract Pair<SectionField, Integer> parseFieldWithNameAndOptionalType(int lineNum, int sectionIndent);

  @NotNull
  protected abstract Pair<String, Integer> parseSectionHeader(int lineNum);

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
    return lineNum < 0 || lineNum >= myLines.size() || isEmpty(lineNum);
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
           (!isEmpty(lineNum) && getIndent(getLine(lineNum)) <= curSectionIndent);
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
      if (getIndent(getLine(lineNum)) > blockIndent) {
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

  /**
   * If both substrings share the same origin, returns new substring that includes both of them. Otherwise return {@code null}.
   *
   * @param s1
   * @param s2 substring to concat with
   * @return new substring as described
   */
  @Nullable
  public static Substring mergeSubstrings(@NotNull Substring s1, @NotNull Substring s2) {
    if (s1.getSuperString().equals(s2.getSuperString())) {
      return new Substring(s1.getSuperString(),
                           Math.min(s1.getStartOffset(), s2.getStartOffset()),
                           Math.max(s1.getEndOffset(), s2.getEndOffset()));
    }
    return null;
  }

  // like Python's textwrap.dedent()
  @NotNull
  protected static String stripCommonIndent(@NotNull Substring text, boolean ignoreFirstStringIfNonEmpty) {
    final List<Substring> lines = text.splitLines();
    if (lines.isEmpty()) {
      return "";
    }
    final String firstLine = lines.get(0).toString();
    final boolean skipFirstLine = ignoreFirstStringIfNonEmpty && !StringUtil.isEmptyOrSpaces(firstLine);
    final Iterable<Substring> workList = lines.subList(skipFirstLine ? 1 : 0, lines.size());
    int curMinIndent = Integer.MAX_VALUE;
    for (Substring line : workList) {
      if (StringUtil.isEmptyOrSpaces(line)) {
        continue;
      }
      curMinIndent = Math.min(curMinIndent, getIndent(line));
    }
    final int minIndent = curMinIndent;
    final List<String> dedentedLines = ContainerUtil.map(workList, new Function<Substring, String>() {
      @Override
      public String fun(Substring line) {
        return line.substring(Math.min(line.length(), minIndent)).toString();
      }
    });
    return StringUtil.join(skipFirstLine ? ContainerUtil.prepend(dedentedLines, firstLine) : dedentedLines, "\n");
  }

  protected static int getIndent(@NotNull CharSequence line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isSpaceChar(line.charAt(i))) {
        return i;
      }
    }
    return 0;
  }

  @NotNull
  protected Substring getLine(int lineNum) {
    return myLines.get(lineNum);
  }

  @Nullable
  protected Substring getLineOrNull(int lineNum) {
    return lineNum >= 0 && lineNum < myLines.size() ? myLines.get(lineNum) : null;
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
    return mySummary != null ? mySummary.concatTrimmedLines("\n") : "";
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
    if (paramName != null) {
      final SectionField field = getFirstFieldForParameter(paramName);
      if (field != null) {
        return field.getType();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Substring getParamTypeSubstring(@Nullable String paramName) {
    if (paramName != null) {
      final SectionField field = getFirstFieldForParameter(paramName);
      if (field != null) {
        return field.getTypeAsSubstring();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getParamDescription(@Nullable String paramName) {
    if (paramName != null) {
      final SectionField field = getFirstFieldForParameter(paramName);
      if (field != null) {
        return field.getDescription();
      }
    }
    return null;
  }

  @Nullable
  private SectionField getFirstFieldForParameter(@NotNull final String name) {
    return ContainerUtil.find(getParameterFields(), new Condition<SectionField>() {
      @Override
      public boolean value(SectionField field) {
        return name.equals(field.getName());
      }
    });
  }

  @NotNull
  private List<SectionField> getParameterFields() {
    final List<SectionField> result = new ArrayList<SectionField>();
    for (Section section : mySections) {
      if (section.getTitle().equals(NORMALIZED_PARAMETERS_SECTION)) {
        for (SectionField field : section.getFields()) {
          result.add(field);
        }
      }
    }
    return result;
  }

  @Override
  public List<String> getKeywordArguments() {
    return ContainerUtil.mapNotNull(getKeywordArgumentFields(), new Function<SectionField, String>() {
      @Override
      public String fun(SectionField field) {
        return field.getName();
      }
    });
  }

  @Override
  public List<Substring> getKeywordArgumentSubstrings() {
    return ContainerUtil.mapNotNull(getKeywordArgumentFields(), new Function<SectionField, Substring>() {
      @Override
      public Substring fun(SectionField field) {
        return field.getNameAsSubstring();
      }
    });
  }

  @Nullable
  @Override
  public String getKeywordArgumentDescription(@Nullable String paramName) {
    if (paramName != null) {
      final SectionField argument = getFirstFieldForKeywordArgument(paramName);
      if (argument != null) {
        return argument.getDescription();
      }
    }
    return null;
  }

  @NotNull
  private List<SectionField> getKeywordArgumentFields() {
    final List<SectionField> result = new ArrayList<SectionField>();
    for (Section section : mySections) {
      if (section.getTitle().equals(NORMALIZED_KEYWORD_ARGUMENTS_SECTION)) {
        for (SectionField field : section.getFields()) {
          result.add(field);
        }
      }
    }
    return result;
  }

  @Nullable
  private SectionField getFirstFieldForKeywordArgument(@NotNull final String name) {
    return ContainerUtil.find(getKeywordArgumentFields(), new Condition<SectionField>() {
      @Override
      public boolean value(SectionField field) {
        return name.equals(field.getName());
      }
    });
  }

  @Nullable
  @Override
  public String getReturnType() {
    final SectionField field = getFirstReturnField();
    return field != null ? field.getType() : null;
  }
  
  @Nullable
  @Override
  public Substring getReturnTypeSubstring() {
    final SectionField field = getFirstReturnField();
    return field != null ? field.getTypeAsSubstring() : null;
  }

  @Nullable
  @Override
  public String getReturnDescription() {
    final SectionField field = getFirstReturnField();
    return field != null ? field.getDescription() : null;
  }

  @Nullable
  private SectionField getFirstReturnField() {
    for (Section section : mySections) {
      if (section.getTitle().equals(NORMALIZED_RETURN_SECTION_NAME) && !section.getFields().isEmpty()) {
        return section.getFields().get(0);
      }
    }
    return null;
  }

  @Override
  public List<String> getRaisedExceptions() {
    return ContainerUtil.mapNotNull(getExceptionFields(), new Function<SectionField, String>() {
      @Override
      public String fun(SectionField field) {
        return field.getType();
      }
    });
  }
  
  @Nullable
  @Override
  public String getRaisedExceptionDescription(@Nullable String exceptionName) {
    if (exceptionName != null) {
      final SectionField exception = getFirstFieldForException(exceptionName);
      if (exception != null) {
        return exception.getDescription();
      }
    }
    return null;
  }

  @NotNull
  private List<SectionField> getExceptionFields() {
    final List<SectionField> result = new ArrayList<SectionField>();
    for (Section section : mySections) {
      if (section.getTitle().equals(NORMALIZED_EXCEPTIONS_SECTION_NAME)) {
        result.addAll(section.getFields());
      }
    }
    return result; 
  }

  @Nullable
  private SectionField getFirstFieldForException(@NotNull final String exceptionType) {
    return ContainerUtil.find(getExceptionFields(), new Condition<SectionField>() {
      @Override
      public boolean value(SectionField field) {
        return exceptionType.equals(field.getType());
      }
    });
  }
  
  @Nullable
  @Override
  public String getAttributeDescription() {
    return null;
  }

  @Nullable
  @Override
  public Substring getParamByNameAndKind(@NotNull String name, String kind) {
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
      return myDescription == null ? "" : stripCommonIndent(myDescription, true);
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
}
