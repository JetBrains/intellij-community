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

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

import static com.jetbrains.python.documentation.docstrings.SectionBasedDocString.FieldType.OPTIONAL_TYPE;

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
  public static final @NonNls String RETURNS_SECTION = "returns";
  public static final @NonNls String RAISES_SECTION = "raises";
  public static final @NonNls String KEYWORD_ARGUMENTS_SECTION = "keyword arguments";
  public static final @NonNls String PARAMETERS_SECTION = "parameters";
  public static final @NonNls String ATTRIBUTES_SECTION = "attributes";
  public static final @NonNls String METHODS_SECTION = "methods";
  public static final @NonNls String OTHER_PARAMETERS_SECTION = "other parameters";
  public static final @NonNls String YIELDS_SECTION = "yields";

  private static final Pattern PLAIN_TEXT = Pattern.compile("\\w+(\\s+\\w+){2}"); // dumb heuristic - consecutive words

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

  public static final Set<String> SECTION_NAMES = SECTION_ALIASES.keySet();

  protected enum FieldType {
    NAME_WITH_OPTIONAL_TYPE(true, false, false),
    TYPE_WITH_OPTIONAL_NAME(true, true, false),
    ONLY_TYPE(false, true, false),
    OPTIONAL_TYPE(false, true, true),
    ONLY_NAME(false, false, false);

    public final boolean canHaveBothNameAndType;
    public final boolean preferType;
    public final boolean canHaveOnlyDescription;

    FieldType(boolean canHaveType, boolean preferType, boolean canHaveOnlyDescription) {
      this.canHaveBothNameAndType = canHaveType;
      this.canHaveOnlyDescription = canHaveOnlyDescription;
      this.preferType = preferType;
    }
  }

  public static @Nullable String getNormalizedSectionTitle(@NotNull @NonNls String title) {
    return SECTION_ALIASES.get(StringUtil.toLowerCase(title));
  }

  public static boolean isValidSectionTitle(@NotNull @NonNls String title) {
    return StringUtil.isCapitalized(title) && getNormalizedSectionTitle(title) != null;
  }

  private final Substring mySummary;
  private final List<Section> mySections = new ArrayList<>();

  protected SectionBasedDocString(@NotNull Substring text) {
    super(text);
    List<Substring> summary = Collections.emptyList();
    int startLine = consumeEmptyLines(parseHeader(0));
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
        lineNum++;
      }
      lineNum = consumeEmptyLines(lineNum);
    }
    mySummary = summary.isEmpty() ? null : summary.get(0).union(summary.get(summary.size() - 1)).trim();
  }

  private @NotNull Pair<List<Substring>, Integer> parseSummary(int lineNum) {
    final List<Substring> result = new ArrayList<>();
    while (!(isEmptyOrDoesNotExist(lineNum) || isBlockEnd(lineNum))) {
      result.add(getLine(lineNum));
      lineNum++;
    }
    return Pair.create(result, lineNum);
  }

  /**
   * Used to parse e.g. optional function signature at the beginning of NumPy-style docstring
   *
   * @return first line from which to start parsing remaining sections
   */
  protected int parseHeader(int startLine) {
    return startLine;
  }

  protected @NotNull Pair<Section, Integer> parseSection(int sectionStartLine) {
    final Pair<Substring, Integer> parsedHeader = parseSectionHeader(sectionStartLine);
    if (parsedHeader.getFirst() == null) {
      return Pair.create(null, sectionStartLine);
    }
    final String normalized = getNormalizedSectionTitle(parsedHeader.getFirst().toString());
    if (normalized == null) {
      return Pair.create(null, sectionStartLine);
    }
    final List<SectionField> fields = new ArrayList<>();
    final int sectionIndent = getLineIndentSize(sectionStartLine);
    int lineNum = consumeEmptyLines(parsedHeader.getSecond());
    while (!isSectionBreak(lineNum, sectionIndent)) {
      if (!isEmpty(lineNum)) {
        final Pair<SectionField, Integer> parsedField = parseSectionField(lineNum, normalized, sectionIndent);
        if (parsedField.getFirst() != null) {
          fields.add(parsedField.getFirst());
          lineNum = parsedField.getSecond();
          continue;
        }
      }
      lineNum++;
    }
    return Pair.create(new Section(parsedHeader.getFirst(), fields), lineNum);
  }

  protected @NotNull Pair<SectionField, Integer> parseSectionField(int lineNum, @NotNull String normalizedSectionTitle, int sectionIndent) {
    final FieldType fieldType = getFieldType(normalizedSectionTitle);
    if (fieldType != null) {
      return parseSectionField(lineNum, sectionIndent, fieldType);
    }
    return parseGenericField(lineNum, sectionIndent);
  }

  protected abstract @Nullable FieldType getFieldType(@NotNull String title);

  protected abstract Pair<SectionField, Integer> parseSectionField(int lineNum, int sectionIndent, @NotNull FieldType kind);

  protected @NotNull Pair<SectionField, Integer> parseGenericField(int lineNum, int sectionIndent) {
    final Pair<List<Substring>, Integer> pair = parseIndentedBlock(lineNum, getSectionIndentationThreshold(sectionIndent));
    final Substring firstLine = ContainerUtil.getFirstItem(pair.getFirst());
    final Substring lastLine = ContainerUtil.getLastItem(pair.getFirst());
    if (firstLine != null && lastLine != null) {
      return Pair.create(new SectionField((Substring)null, null, firstLine.union(lastLine).trim()), pair.getSecond());
    }
    return Pair.create(null, pair.getSecond());
  }

  protected abstract @NotNull Pair<Substring, Integer> parseSectionHeader(int lineNum);

  protected boolean isSectionStart(int lineNum) {
    final Pair<Substring, Integer> pair = parseSectionHeader(lineNum);
    return pair.getFirst() != null;
  }

  protected boolean isSectionBreak(int lineNum, int curSectionIndent) {
    return lineNum >= getLineCount() ||
           // note that field may have the same indent as its containing section
           (!isEmpty(lineNum) && getLineIndentSize(lineNum) <= getSectionIndentationThreshold(curSectionIndent)) ||
           isSectionStart(lineNum);
  }

  protected @NotNull Pair<List<Substring>, Integer> parseFieldContinuation(int lineNum, @NotNull FieldType fieldType) {
    int indent = getLineIndentSize(lineNum);
    // we don't need additional indentation for Yields and Returns sections
    return parseIndentedBlock(lineNum + 1, fieldType == OPTIONAL_TYPE ? indent - 1 : indent);
  }

  /**
   * Consumes all lines that are indented more than {@code blockIndent} and don't contain start of a new section.
   * Trailing empty lines (e.g. due to indentation of closing triple quotes) are omitted in result.
   *
   * @param blockIndent indentation threshold, block ends with a line that has greater indentation
   */
  protected @NotNull Pair<List<Substring>, Integer> parseIndentedBlock(int lineNum, int blockIndent) {
    final int blockEnd = consumeIndentedBlock(lineNum, blockIndent);
    return Pair.create(myLines.subList(lineNum, blockEnd), blockEnd);
  }

  /**
   * Inside section any indentation that is equal or smaller to returned one signals about section break.
   * It's safe to return negative value, because it's used only for comparisons.
   *
   * @see #isSectionBreak(int, int)
   * @see #parseGenericField(int, int)
   */
  protected int getSectionIndentationThreshold(int sectionIndent) {
    return sectionIndent;
  }

  @Override
  protected boolean isBlockEnd(int lineNum) {
    return isSectionStart(lineNum);
  }

  protected boolean isValidType(@NotNull String type) {
    return !type.isEmpty() && !isPlainText(type);
  }

  protected static boolean isPlainText(@NotNull String type) {
    return PLAIN_TEXT.matcher(type).find();
  }

  protected boolean isValidName(@NotNull String name) {
    return PyNames.isIdentifierString(name);
  }

  /**
   * Properly partitions line by first colon taking into account possible Sphinx references inside
   * <p/>
   * <h3>Example</h3>
   * <pre>{@code
   *   runtime (:class:`Runtime`): Use it to access the environment.
   * }</pre>
   */
  protected static @NotNull List<Substring> splitByFirstColon(@NotNull Substring line) {
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

  public @NotNull List<Section> getSections() {
    return Collections.unmodifiableList(mySections);
  }

  @Override
  public String getSummary() {
    return mySummary != null ? mySummary.concatTrimmedLines("\n") : "";
  }

  @Override
  public @NotNull String getDescription() {
    return "";
  }

  @Override
  public @NotNull List<String> getParameters() {
    return ContainerUtil.map(getParameterSubstrings(), substring -> substring.toString());
  }

  @Override
  public @NotNull List<Substring> getParameterSubstrings() {
    final List<Substring> result = new ArrayList<>();
    for (SectionField field : getParameterFields()) {
      ContainerUtil.addAllNotNull(result, field.getNamesAsSubstrings());
    }
    return result;
  }

  @Override
  public @Nullable String getParamType(@Nullable String paramName) {
    final Substring sub = getParamTypeSubstring(paramName);
    return sub != null ? sub.toString() : null;
  }

  @Override
  public @Nullable Substring getParamTypeSubstring(@Nullable String paramName) {
    if (paramName != null) {
      final SectionField field = getFirstFieldForParameter(paramName);
      if (field != null) {
        return field.getTypeAsSubstring();
      }
    }
    return null;
  }

  @Override
  public @Nullable String getParamDescription(@Nullable String paramName) {
    if (paramName != null) {
      final SectionField field = getFirstFieldForParameter(paramName);
      if (field != null) {
        return field.getDescription();
      }
    }
    return null;
  }

  public @Nullable SectionField getFirstFieldForParameter(final @NotNull String name) {
    return ContainerUtil.find(getParameterFields(), field -> field.getNames().contains(name));
  }

  public @NotNull List<SectionField> getParameterFields() {
    final List<SectionField> result = new ArrayList<>();
    for (Section section : getParameterSections()) {
      result.addAll(section.getFields());
    }
    return result;
  }

  public @NotNull List<Section> getParameterSections() {
    final List<Section> parameters = new ArrayList<>();
    parameters.addAll(getSectionsWithNormalizedTitle(PARAMETERS_SECTION));
    parameters.addAll(getSectionsWithNormalizedTitle(OTHER_PARAMETERS_SECTION));
    return parameters;
  }

  @Override
  public @NotNull List<String> getKeywordArguments() {
    final List<String> result = new ArrayList<>();
    for (SectionField field : getKeywordArgumentFields()) {
      result.addAll(field.getNames());
    }
    return result;
  }

  @Override
  public @NotNull List<Substring> getKeywordArgumentSubstrings() {
    final List<Substring> result = new ArrayList<>();
    for (SectionField field : getKeywordArgumentFields()) {
      ContainerUtil.addAllNotNull(field.getNamesAsSubstrings());
    }
    return result;
  }

  @Override
  public @Nullable String getKeywordArgumentDescription(@Nullable String paramName) {
    if (paramName != null) {
      final SectionField argument = getFirstFieldForKeywordArgument(paramName);
      if (argument != null) {
        return argument.getDescription();
      }
    }
    return null;
  }

  public @NotNull List<SectionField> getKeywordArgumentFields() {
    final List<SectionField> result = new ArrayList<>();
    for (Section section : getSectionsWithNormalizedTitle(KEYWORD_ARGUMENTS_SECTION)) {
      result.addAll(section.getFields());
    }
    return result;
  }

  private @Nullable SectionField getFirstFieldForKeywordArgument(final @NotNull String name) {
    return ContainerUtil.find(getKeywordArgumentFields(), field -> field.getNames().contains(name));
  }

  @Override
  public @Nullable String getReturnType() {
    final Substring sub = getReturnTypeSubstring();
    return sub != null ? sub.toString() : null;
  }

  @Override
  public @Nullable Substring getReturnTypeSubstring() {
    final SectionField field = getFirstReturnField();
    return field != null ? field.getTypeAsSubstring() : null;
  }

  @Override
  public @Nullable String getReturnDescription() {
    final SectionField field = getFirstReturnField();
    return field != null ? field.getDescription() : null;
  }


  public @NotNull List<SectionField> getReturnFields() {
    final List<SectionField> result = new ArrayList<>();
    for (Section section : getSectionsWithNormalizedTitle(RETURNS_SECTION)) {
      result.addAll(section.getFields());
    }
    return result;
  }

  private @Nullable SectionField getFirstReturnField() {
    return ContainerUtil.getFirstItem(getReturnFields());
  }

  @Override
  public @NotNull List<String> getRaisedExceptions() {
    return ContainerUtil.mapNotNull(getExceptionFields(), field -> StringUtil.nullize(field.getType()));
  }

  @Override
  public @Nullable String getRaisedExceptionDescription(@Nullable String exceptionName) {
    if (exceptionName != null) {
      final SectionField exception = getFirstFieldForException(exceptionName);
      if (exception != null) {
        return exception.getDescription();
      }
    }
    return null;
  }

  public @NotNull List<SectionField> getExceptionFields() {
    final List<SectionField> result = new ArrayList<>();
    for (Section section : getSectionsWithNormalizedTitle(RAISES_SECTION)) {
      result.addAll(section.getFields());
    }
    return result;
  }

  private @Nullable SectionField getFirstFieldForException(final @NotNull String exceptionType) {
    return ContainerUtil.find(getExceptionFields(), field -> exceptionType.equals(field.getType()));
  }

  public @NotNull List<SectionField> getAttributeFields() {
    final List<SectionField> result = new ArrayList<>();
    for (Section section : getSectionsWithNormalizedTitle(ATTRIBUTES_SECTION)) {
      result.addAll(section.getFields());
    }
    return result;
  }

  public @NotNull List<Section> getSectionsWithNormalizedTitle(final @NotNull String title) {
    return ContainerUtil.mapNotNull(mySections,
                                    section -> section.getNormalizedTitle().equals(getNormalizedSectionTitle(title)) ? section : null);
  }

  public @Nullable Section getFirstSectionWithNormalizedTitle(@NotNull String title) {
    return ContainerUtil.getFirstItem(getSectionsWithNormalizedTitle(title));
  }

  @Override
  public @Nullable String getAttributeDescription() {
    return null;
  }

  @Override
  public @Nullable String getAttributeDescription(@Nullable String name) {
    if (name != null) {
      final SectionField field = getFirstFieldForAttribute(name);
      if (field != null) {
        return field.getDescription();
      }
    }
    return null;
  }

  public @Nullable SectionField getFirstFieldForAttribute(@NotNull String name) {
    return ContainerUtil.find(getAttributeFields(), field -> field.getNames().contains(name));
  }

  @Override
  public @NotNull List<String> getAttributes() {
    return ContainerUtil.map(getAttributeSubstrings(), substring -> substring.toString());
  }

  @Override
  public @NotNull List<Substring> getAttributeSubstrings() {
    final List<Substring> result = new ArrayList<>();
    for (SectionField field : getAttributeFields()) {
      ContainerUtil.addAllNotNull(result, field.getNamesAsSubstrings());
    }
    return result;
  }

  protected static @NotNull Substring cleanUpName(@NotNull Substring name) {
    int firstNotStar = 0;
    while (firstNotStar < name.length() && name.charAt(firstNotStar) == '*') {
      firstNotStar++;
    }
    return name.substring(firstNotStar).trimLeft();
  }

  public static class Section {
    private final Substring myTitle;
    private final List<SectionField> myFields;

    public Section(@NotNull Substring title, @NotNull List<SectionField> fields) {
      myTitle = title;
      myFields = new ArrayList<>(fields);
    }

    public @NotNull Substring getTitleAsSubstring() {
      return myTitle;
    }

    public @NotNull String getTitle() {
      return myTitle.toString();
    }

    public @NotNull String getNormalizedTitle() {
      //noinspection ConstantConditions
      return getNormalizedSectionTitle(getTitle());
    }

    public @NotNull List<SectionField> getFields() {
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
    private final List<Substring> myNames;
    private final Substring myType;
    private final Substring myDescription;

    public SectionField(@Nullable Substring name, @Nullable Substring type, @Nullable Substring description) {
      this(ContainerUtil.createMaybeSingletonList(name), type, description);
    }

    public SectionField(@NotNull List<Substring> names, @Nullable Substring type, @Nullable Substring description) {
      myNames = names;
      myType = type;
      myDescription = description;
    }

    public @Nullable String getName() {
      return myNames.isEmpty() ? null : myNames.get(0).toString();
    }

    public @Nullable Substring getNameAsSubstring() {
      return myNames.isEmpty() ? null : myNames.get(0);
    }

    public @NotNull List<Substring> getNamesAsSubstrings() {
      return myNames;
    }

    public @NotNull List<String> getNames() {
      return ContainerUtil.map(myNames, substring -> substring.toString());
    }

    public @Nullable String getType() {
      return myType == null ? null : myType.toString();
    }

    public @Nullable Substring getTypeAsSubstring() {
      return myType;
    }

    public @Nullable String getDescription() {
      return myDescription == null ? null : PyIndentUtil.removeCommonIndent(myDescription.getValue(), true);
    }

    public @Nullable Substring getDescriptionAsSubstring() {
      return myDescription;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SectionField field = (SectionField)o;

      if (myNames != null ? !myNames.equals(field.myNames) : field.myNames != null) return false;
      if (myType != null ? !myType.equals(field.myType) : field.myType != null) return false;
      if (myDescription != null ? !myDescription.equals(field.myDescription) : field.myDescription != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myNames != null ? myNames.hashCode() : 0;
      result = 31 * result + (myType != null ? myType.hashCode() : 0);
      result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
      return result;
    }
  }
}
