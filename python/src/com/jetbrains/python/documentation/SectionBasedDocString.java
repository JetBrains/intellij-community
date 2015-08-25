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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Common base class for docstring styles supported by Napoleon Sphinx extension.
 *
 * @author Mikhail Golubev
 * @see <a href="http://sphinxcontrib-napoleon.readthedocs.org/en/latest/index.html">Napoleon</a>
 */
public abstract class SectionBasedDocString extends DocStringLineParser implements StructuredDocString {

  /**
   * Frequently used section types
   */
  @NonNls public static final String RETURNS_SECTION = "returns";
  @NonNls public static final String RAISES_SECTION = "raises";
  @NonNls public static final String KEYWORD_ARGUMENTS_SECTION = "keyword arguments";
  @NonNls public static final String PARAMETERS_SECTION = "parameters";
  @NonNls public static final String ATTRIBUTES_SECTION = "attributes";
  @NonNls public static final String METHODS_SECTION = "methods";
  @NonNls public static final String OTHER_PARAMETERS_SECTION = "other parameters";
  @NonNls public static final String YIELDS_SECTION = "yields";

  protected static final Map<String, String> SECTION_ALIASES =
    ImmutableMap.<String, String>builder()
                .put("arguments", PARAMETERS_SECTION)
                .put("args", PARAMETERS_SECTION)
                .put("parameters", PARAMETERS_SECTION)
                .put("keyword args", KEYWORD_ARGUMENTS_SECTION)
                .put("keyword arguments", KEYWORD_ARGUMENTS_SECTION)
                .put("other parameters", OTHER_PARAMETERS_SECTION)
                .put("attributes", ATTRIBUTES_SECTION)
                .put("methods", METHODS_SECTION)
                .put("note", "notes")
                .put("notes", "notes")
                .put("example", "examples")
                .put("examples", "examples")
                .put("return", RETURNS_SECTION)
                .put("returns", RETURNS_SECTION)
                .put("yield", YIELDS_SECTION)
                .put("yields", "yields")
                .put("raises", RAISES_SECTION)
                .put("references", "references")
                .put("see also", "see also")
                .put("warning", "warnings")
                .put("warns", "warnings")
                .put("warnings", "warnings")
                .build();
  private static final Pattern SPHINX_REFERENCE_RE = Pattern.compile("(:\\w+:\\S+:`.+?`|:\\S+:`.+?`|`.+?`)");

  public static Set<String> SECTION_NAMES = SECTION_ALIASES.keySet();
  private static final ImmutableSet<String> SECTIONS_WITH_NAME_AND_OPTIONAL_TYPE = ImmutableSet.of(ATTRIBUTES_SECTION,
                                                                                                   PARAMETERS_SECTION,
                                                                                                   KEYWORD_ARGUMENTS_SECTION,
                                                                                                   OTHER_PARAMETERS_SECTION);
  private static final ImmutableSet<String> SECTIONS_WITH_TYPE_AND_OPTIONAL_NAME = ImmutableSet.of(RETURNS_SECTION, YIELDS_SECTION);
  private static final ImmutableSet<String> SECTIONS_WITH_TYPE = ImmutableSet.of(RAISES_SECTION);
  private static final ImmutableSet<String> SECTIONS_WITH_NAME = ImmutableSet.of(METHODS_SECTION);

  @Nullable
  protected static String normalizeSectionTitle(@NotNull @NonNls String title) {
    return SECTION_ALIASES.get(title.toLowerCase());
  }

  private final Substring mySummary;
  private final List<Section> mySections = new ArrayList<Section>();
  private final List<Substring> myOtherContent = new ArrayList<Substring>();

  protected SectionBasedDocString(@NotNull Substring text) {
    super(text);
    List<Substring> summary = Collections.emptyList();
    int startLine = skipEmptyLines(parseHeader(0));
    int lineNum = startLine;
    while (lineNum < getLineCount()) {
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
    final Pair<Substring, Integer> pair = parseSectionHeader(sectionStartLine);
    if (pair.getFirst() == null) {
      return Pair.create(null, sectionStartLine);
    }
    final String normalized = normalizeSectionTitle(pair.getFirst().toString());
    if (normalized == null) {
      return Pair.create(null, sectionStartLine);
    }
    int lineNum = skipEmptyLines(pair.getSecond());
    final List<SectionField> fields = new ArrayList<SectionField>();
    final int sectionIndent = getLineIndentSize(sectionStartLine);
    while (!isSectionBreak(lineNum, sectionIndent)) {
      if (!isEmpty(lineNum)) {
        final Pair<SectionField, Integer> result = parseSectionField(lineNum, normalized, sectionIndent);
        if (result.getFirst() != null) {
          fields.add(result.getFirst());
          lineNum = result.getSecond();
          continue;
        }
      }
      lineNum++;
    }
    return Pair.create(new Section(pair.getFirst(), fields), lineNum);
  }

  @NotNull
  protected Pair<SectionField, Integer> parseSectionField(int lineNum, @NotNull String normalizedSectionTitle, int sectionIndent) {
    if (SECTIONS_WITH_NAME_AND_OPTIONAL_TYPE.contains(normalizedSectionTitle)) {
      return parseSectionField(lineNum, sectionIndent, true, false);
    }
    if (SECTIONS_WITH_TYPE_AND_OPTIONAL_NAME.contains(normalizedSectionTitle)) {
      return parseSectionField(lineNum, sectionIndent, true, true);
    }
    if (SECTIONS_WITH_NAME.contains(normalizedSectionTitle)) {
      return parseSectionField(lineNum, sectionIndent, false, false);
    }
    if (SECTIONS_WITH_TYPE.contains(normalizedSectionTitle)) {
      return parseSectionField(lineNum, sectionIndent, false, true);
    }
    return parseGenericField(lineNum, sectionIndent);
  }

  protected abstract Pair<SectionField, Integer> parseSectionField(int lineNum,
                                                                   int sectionIndent,
                                                                   boolean mayHaveType,
                                                                   boolean preferType);

  @NotNull
  protected Pair<SectionField, Integer> parseGenericField(int lineNum, int sectionIndent) {
    final Pair<List<Substring>, Integer> pair = parseIndentedBlock(lineNum, sectionIndent, sectionIndent);
    final Substring firstLine = ContainerUtil.getFirstItem(pair.getFirst());
    final Substring lastLine = ContainerUtil.getLastItem(pair.getFirst());
    if (firstLine != null && lastLine != null) {
      return Pair.create(new SectionField(null, null, mergeSubstrings(firstLine, lastLine).trim()), pair.getSecond());
    }
    return Pair.create(null, pair.getSecond());
  }

  @NotNull
  protected abstract Pair<Substring, Integer> parseSectionHeader(int lineNum);

  protected int skipEmptyLines(int lineNum) {
    while (lineNum < getLineCount() && isEmpty(lineNum)) {
      lineNum++;
    }
    return lineNum;
  }

  protected boolean isSectionStart(int lineNum) {
    final Pair<Substring, Integer> pair = parseSectionHeader(lineNum);
    return pair.getFirst() != null;
  }

  protected boolean isSectionBreak(int lineNum, int curSectionIndent) {
    return lineNum >= getLineCount() ||
           isSectionStart(lineNum) ||
           (!isEmpty(lineNum) && getLineIndentSize(lineNum) <= curSectionIndent);
  }

  /**
   * Consumes all lines that are indented more than {@code blockIndent} and don't contain start of a new section.
   * Trailing empty lines (e.g. due to indentation of closing triple quotes) are omitted in result.
   *
   * @param blockIndent indentation threshold, block ends with a line that has greater indentation
   */
  @NotNull
  protected Pair<List<Substring>, Integer> parseIndentedBlock(int lineNum, int blockIndent, int sectionIndent) {
    final List<Substring> result = new ArrayList<Substring>();
    int lastNonEmpty = lineNum - 1;
    while (!isSectionBreak(lineNum, sectionIndent)) {
      if (getLineIndentSize(lineNum) > blockIndent) {
        // copy all lines after the last non empty including the current one
        for (int i = lastNonEmpty + 1; i <= lineNum; i++) {
          result.add(getLine(i));
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
   * Properly partitions line by first colon taking into account possible Sphinx references inside
   * <p/>
   * <h3>Example</h3>
   * <pre><code>
   *   runtime (:class:`Runtime`): Use it to access the environment.
   * </code></pre>
   */
  @NotNull
  protected static List<Substring> splitByFirstColon(@NotNull Substring line) {
    final List<Substring> parts = line.split(SPHINX_REFERENCE_RE);
    if (parts.size() > 1) {
      for (Substring part : parts) {
        final int i = part.indexOf(":");
        if (i >= 0) {
          final Substring beforeColon = new Substring(line.getSuperString(), line.getStartOffset(), part.getStartOffset() + i);
          final Substring afterColon = new Substring(line.getSuperString(), part.getStartOffset() + i + 1, line.getEndOffset());
          return Arrays.asList(beforeColon, afterColon);
        }
      }
      return Collections.singletonList(line);
    }
    return line.split(":", 1);
  }

  /**
   * If both substrings share the same origin, returns new substring that includes both of them. Otherwise return {@code null}.
   *
   * @param s2 substring to concat with
   * @return new substring as described
   */
  @NotNull
  protected static Substring mergeSubstrings(@NotNull Substring s1, @NotNull Substring s2) {
    if (!s1.getSuperString().equals(s2.getSuperString())) {
      throw new IllegalArgumentException(String.format("Substrings '%s' and '%s' must belong to the same origin", s1, s2));
    }
    return new Substring(s1.getSuperString(),
                       Math.min(s1.getStartOffset(), s2.getStartOffset()),
                       Math.max(s1.getEndOffset(), s2.getEndOffset()));
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
      curMinIndent = Math.min(curMinIndent, PyIndentUtil.getLineIndentSize(line));
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

  @NotNull
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
    return ContainerUtil.mapNotNull(getParameterFields(), new Function<SectionField, Substring>() {
      @Override
      public Substring fun(SectionField field) {
        return field.getNameAsSubstring();
      }
    });
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
  public List<SectionField> getParameterFields() {
    final List<SectionField> result = new ArrayList<SectionField>();
    for (Section section : getSectionsWithNormalizedTitle(PARAMETERS_SECTION)) {
      result.addAll(section.getFields());
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
  public List<SectionField> getKeywordArgumentFields() {
    final List<SectionField> result = new ArrayList<SectionField>();
    for (Section section : getSectionsWithNormalizedTitle(KEYWORD_ARGUMENTS_SECTION)) {
      result.addAll(section.getFields());
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


  @NotNull
  public List<SectionField> getReturnFields() {
    final List<SectionField> result = new ArrayList<SectionField>();
    for (Section section : getSectionsWithNormalizedTitle(RETURNS_SECTION)) {
      result.addAll(section.getFields());
    }
    return result;
  }
  
  @Nullable
  private SectionField getFirstReturnField() {
    return ContainerUtil.getFirstItem(getReturnFields());
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
  public List<SectionField> getExceptionFields() {
    final List<SectionField> result = new ArrayList<SectionField>();
    for (Section section : getSectionsWithNormalizedTitle(RAISES_SECTION)) {
      result.addAll(section.getFields());
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

  @NotNull
  public List<SectionField> getAttributeFields() {
    final List<SectionField> result = new ArrayList<SectionField>();
    for (Section section : getSectionsWithNormalizedTitle(ATTRIBUTES_SECTION)) {
      result.addAll(section.getFields());
    }
    return result;
  }

  @NotNull
  private List<Section> getSectionsWithNormalizedTitle(@NotNull final String title) {
    return ContainerUtil.mapNotNull(mySections, new Function<Section, Section>() {
      @Override
      public Section fun(Section section) {
        return section.getNormalizedTitle().equals(title) ? section : null;
      }
    });
  }

  @Nullable
  @Override
  public String getAttributeDescription() {
    return null;
  }

  public static class Section {
    private final Substring myTitle;
    private final List<SectionField> myFields;

    public Section(@NotNull Substring title, @NotNull List<SectionField> fields) {
      myTitle = title;
      myFields = new ArrayList<SectionField>(fields);
    }

    @NotNull
    public Substring getTitleAsSubstring() {
      return myTitle;
    }

    @NotNull
    public String getTitle() {
      return myTitle.toString();
    }
    
    @NotNull
    public String getNormalizedTitle() {
      //noinspection ConstantConditions
      return normalizeSectionTitle(getTitle());
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
