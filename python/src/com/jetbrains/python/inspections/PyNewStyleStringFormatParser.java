/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Mikhail Golubev
 */
public class PyNewStyleStringFormatParser {
  private int myImplicitlyNumberedFieldsCounter = 0;
  private final List<Field> myTopLevelFields = new ArrayList<>();
  private final List<Integer> mySingleRightBraces = new ArrayList<>();
  private final String myNodeText;
  private final TextRange myNodeContentRange;

  @NotNull
  public static ParseResult parse(@NotNull String nodeText) {
    final PyNewStyleStringFormatParser parser = new PyNewStyleStringFormatParser(nodeText);
    parser.parseTopLevel();
    return new ParseResult(parser);
  }

  public static class ParseResult {
    private final PyNewStyleStringFormatParser myParser;

    public ParseResult(@NotNull PyNewStyleStringFormatParser parser) {
      myParser = parser;
    }

    @NotNull
    public List<Field> getFields() {
      return Collections.unmodifiableList(myParser.myTopLevelFields);
    }

    @NotNull
    public List<Field> getAllFields() {
      final List<Field> result = new ArrayList<>();
      collectNestedLists(myParser.myTopLevelFields, result);
      return Collections.unmodifiableList(result);
    }

    @NotNull
    public List<Integer> getSingleRightBraces() {
      return Collections.unmodifiableList(myParser.mySingleRightBraces);
    }

    private static void collectNestedLists(@NotNull List<Field> fields, @NotNull List<Field> result) {
      for (Field field : fields) {
        result.add(field);
        collectNestedLists(field.getNestedFields(), result);
      }
    }
  }


  private PyNewStyleStringFormatParser(@NotNull String nodeText) {
    myNodeText = nodeText;
    myNodeContentRange = PyStringLiteralExpressionImpl.getNodeTextRange(nodeText);
  }

  private void parseTopLevel() {
    int offset = myNodeContentRange.getStartOffset();
    while (offset < myNodeContentRange.getEndOffset()) {
      // First, skip named unicode escapes like "\N{LATIN SMALL LETTER A}" wherever they are
      final int nextOffset = skipNamedUnicodeEscape(offset);
      if (offset != nextOffset) {
        offset = nextOffset;
        continue;
      }

      final char c1 = myNodeText.charAt(offset);
      final char c2 = offset + 1 < myNodeContentRange.getEndOffset() ? myNodeText.charAt(offset + 1) : '\0';

      if ((c1 == '{' && c2 == '{') || (c1 == '}' && c2 == '}')) {
        offset += 2;
        continue;
      }
      else if (c1 == '{') {
        final Field field = parseField(offset, 1);
        myTopLevelFields.add(field);
        offset = field.getFieldEnd();
        continue;
      }
      // Will be marked as errors
      else if (c1 == '}') {
        mySingleRightBraces.add(offset);
      }
      offset++;
    }
  }

  @NotNull
  private Field parseField(int startOffset, int recursionDepth) {
    assert myNodeText.charAt(startOffset) == '{';

    int autoFieldNumber = myImplicitlyNumberedFieldsCounter;

    // in the order of appearance inside a field
    final TIntArrayList attrAndLookupBounds = new TIntArrayList();
    int conversionStart = -1;
    int formatSpecStart = -1;
    final List<Field> nestedFields = new ArrayList<>();
    int rightBraceOffset = -1;

    boolean insideItem = false;
    boolean recovering = false;

    final int contentEnd = myNodeContentRange.getEndOffset();
    int offset = startOffset + 1;
    while (offset < contentEnd) {
      final int nextOffset = skipNamedUnicodeEscape(offset);
      if (offset != nextOffset) {
        offset = nextOffset;
        continue;
      }

      final char c = myNodeText.charAt(offset);
      // inside "name" part of the field
      if (conversionStart == -1 && formatSpecStart == -1) {
        // '{' can appear inside a lookup item, but everywhere else it means that field ends
        if (insideItem) {
          // inside lookup item skip everything up to the closing bracket
          if (c == ']') {
            insideItem = false;
            // remember the end offset of the lookup now, since later we may enter "recovering" state
            attrAndLookupBounds.add(offset + 1);
            // if the next character is neither '.', not '[' stop matching attributes and lookups here
            recovering = offset + 1 < contentEnd && !isAnyCharOf(myNodeText.charAt(offset + 1), ".[");
          }
        }
        else if (isAnyCharOf(c, "[.:!}")) {
          insideItem = c == '[';
          if (!recovering) {
            // avoid duplicate offsets in sequences like "]." or "]["
            addIfNotLastItem(attrAndLookupBounds, offset);
            
            // no name in the field, increment implicitly named fields counter
            if (attrAndLookupBounds.size() == 1 && attrAndLookupBounds.get(0) == startOffset + 1) {
              myImplicitlyNumberedFieldsCounter++;
            }
          }

          if (c == ':') {
            formatSpecStart = offset;
          }
          else if (c == '!') {
            conversionStart = offset;
          }
          else if (c == '}') {
            rightBraceOffset = offset;
            break;
          }
        }
      }
      else if (c == '}') {
        rightBraceOffset = offset;
        break;
      }
      else if (conversionStart >= 0) {
        if (c == ':') {
          formatSpecStart = offset;
        }
      }
      else if (formatSpecStart >= 0) {
        if (c == '{') {
          final Field field = parseField(offset, recursionDepth + 1);
          nestedFields.add(field);
          offset = field.getFieldEnd();
          continue;
        }
      }
      offset++;
    }

    // finish with the trailing attribute or the first name if the field ended unexpectedly
    if (offset >= contentEnd && conversionStart == -1 && formatSpecStart == -1 && !insideItem && !recovering) {
      addIfNotLastItem(attrAndLookupBounds, contentEnd);
    }

    assert !attrAndLookupBounds.isEmpty();

    return new Field(myNodeText,
                     startOffset,
                     attrAndLookupBounds.toNativeArray(),
                     conversionStart,
                     formatSpecStart,
                     nestedFields,
                     rightBraceOffset,
                     rightBraceOffset == -1 ? contentEnd : rightBraceOffset + 1,
                     autoFieldNumber,
                     recursionDepth);
  }

  private static void addIfNotLastItem(TIntArrayList attrAndLookupBounds, int offset) {
    if (attrAndLookupBounds.isEmpty() || attrAndLookupBounds.get(attrAndLookupBounds.size() - 1) != offset) {
      attrAndLookupBounds.add(offset);
    }
  }

  private static boolean isAnyCharOf(char c, @NotNull String variants) {
    return variants.indexOf(c) >= 0;
  }


  private int skipNamedUnicodeEscape(int offset) {
    if (StringUtil.startsWith(myNodeText, offset, "\\N{")) {
      final int rightBraceOffset = myNodeText.indexOf('}', offset + 3);
      return rightBraceOffset < 0 ? myNodeContentRange.getEndOffset() : rightBraceOffset + 1;
    }
    return offset;
  }

  public static class Field extends PyStringFormatParser.SubstitutionChunk {

    private final String myNodeText;
    private final int myLeftBraceOffset;
    private final int[] myAttributesAndLookups;
    private final int myConversionOffset;
    private final int myFormatSpecOffset;
    private final List<Field> myNestedFields;
    private final int myRightBraceOffset;
    private final int myEndOffset;


    private final int myDepth;

    private Field(@NotNull String nodeText,
                  int leftBraceOffset,
                  @NotNull int[] attrAndLookupBounds,
                  int conversionOffset,
                  int formatSpecOffset,
                  @NotNull List<Field> fields,
                  int rightBraceOffset,
                  int endOffset,
                  int autoPosition,
                  int depth) {
      super(leftBraceOffset, endOffset);
      myNodeText = nodeText;
      myLeftBraceOffset = leftBraceOffset;
      myAttributesAndLookups = attrAndLookupBounds;
      myConversionOffset = conversionOffset;
      myFormatSpecOffset = formatSpecOffset;
      myRightBraceOffset = rightBraceOffset;
      myNestedFields = fields;
      myEndOffset = endOffset;
      myDepth = depth;

      final String name = getFirstName();
      if (name.isEmpty()) {
        setAutoPosition(autoPosition);
      }
      else {
        try {
          setManualPosition(Integer.parseInt(name));
        }
        catch (NumberFormatException e) {
          setMappingKey(StringUtil.nullize(name));
        }
      }
    }

    private int defaultToContentEnd(int offset) {
      return offset >= 0 ? offset : myEndOffset;
    }

    public int getLeftBraceOffset() {
      return myLeftBraceOffset;
    }

    /**
     * @return offset of the character following the closing brace or the end of string content if it's not present.
     */
    public int getFieldEnd() {
      return myEndOffset;
    }

    /**
     * @return the offset of the closing brace or -1 if it's not present
     */
    public int getRightBraceOffset() {
      return myRightBraceOffset;
    }


    /**
     * The identifier (presumably, valid) or the index in the name part of the field after "{" and before the first "." or "[".
     * It's always present, but might be empty. Depending on its content either {@link #getMappingKey()}, {@link #getManualPosition()} or
     * {@link #getAutoPosition()} returns non-null value.
     *
     * @see #getFirstNameRange()
     * @see #getMappingKey()
     * @see #getManualPosition()
     * @see #getAutoPosition()
     */
    @NotNull
    public String getFirstName() {
      return getFirstNameRange().substring(myNodeText);
    }

    @NotNull
    public TextRange getFirstNameRange() {
      return TextRange.create(myLeftBraceOffset + 1, myAttributesAndLookups[0]);
    }

    /**
     * The range of the part after "{" (not including it) up to the "!", ":", "}" or the end of string content.
     */
    @NotNull
    public TextRange getNamePartRange() {
      final int end = Math.min(Math.min(defaultToContentEnd(myFormatSpecOffset),
                                        defaultToContentEnd(myConversionOffset)),
                               defaultToContentEnd(myRightBraceOffset));
      return TextRange.create(myLeftBraceOffset + 1, end);
    }

    /**
     * The part after "!" (including it) up to the ":", "}" or the end of string literal content.
     *
     * @see #getConversionRange()
     */
    @Nullable
    public String getConversion() {
      final TextRange range = getConversionRange();
      return range != null ? range.substring(myNodeText) : null;
    }

    @Nullable
    public TextRange getConversionRange() {
      final int end = Math.min(defaultToContentEnd(myFormatSpecOffset), defaultToContentEnd(myRightBraceOffset));
      return myConversionOffset >= 0 ? TextRange.create(myConversionOffset, end) : null;
    }

    /**
     * The part after ":" (including it) up to the "}" or the end of string literal content.
     *
     * @see #getConversionRange()
     */
    @Nullable
    public String getFormatSpec() {
      final TextRange range = getFormatSpecRange();
      return range != null ? range.substring(myNodeText) : null;
    }

    @Nullable
    public TextRange getFormatSpecRange() {
      return myFormatSpecOffset >= 0 ? TextRange.create(myFormatSpecOffset, defaultToContentEnd(myRightBraceOffset)) : null;
    }

    /**
     * Nested fields that occurred in the format specification part this this one.
     */
    @NotNull
    public List<Field> getNestedFields() {
      return Collections.unmodifiableList(myNestedFields);
    }

    /**
     * Lookups and attribute references following name or index of the field in form of strings like ".foo" or "[bar]".
     */
    @NotNull
    public List<String> getAttributesAndLookups() {
      return getAttributesAndLookupsRanges().stream().map(ranges -> ranges.substring(myNodeText)).collect(Collectors.toList());
    }

    @NotNull
    public List<TextRange> getAttributesAndLookupsRanges() {
      final List<TextRange> result = new ArrayList<>();
      for (int i = 0; i < myAttributesAndLookups.length - 1; i++) {
        result.add(TextRange.create(myAttributesAndLookups[i], myAttributesAndLookups[i + 1]));
      }
      return Collections.unmodifiableList(result);
    }

    /**
     * The level of how deep this field is nested in the string literal. Level of 0 means a top-level field, level of 1 means a field
     * that is in the format specification of a top-level field, etc.
     * <p>
     * According to PEP 3101, fields nested deeper that twice are not allowed.
     *
     * @see #getNestedFields()
     */
    public int getDepth() {
      return myDepth;
    }
  }
}
