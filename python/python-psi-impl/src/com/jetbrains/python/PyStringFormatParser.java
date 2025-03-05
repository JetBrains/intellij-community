/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStringLiteralDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PyStringFormatParser {
  private static final Pattern NEW_STYLE_FORMAT_TOKENS = Pattern.compile("(\\{\\{)|(\\}\\})|(\\{[^\\{\\}]*\\})|([^\\{\\}]+)");

  public abstract static class FormatStringChunk {
    private final int myStartIndex;
    protected int myEndIndex;

    public FormatStringChunk(int startIndex, int endIndex) {
      myStartIndex = startIndex;
      myEndIndex = endIndex;
    }

    public int getStartIndex() {
      return myStartIndex;
    }

    public int getEndIndex() {
      return myEndIndex;
    }

    public @NotNull TextRange getTextRange() {
      return TextRange.create(myStartIndex, myEndIndex);
    }
  }

  public static class ConstantChunk extends FormatStringChunk {

    public ConstantChunk(int startIndex, int endIndex) {
      super(startIndex, endIndex);
    }
  }

  public static class SubstitutionChunk extends FormatStringChunk {
    private @Nullable String myMappingKey;
    private @Nullable String myWidth;
    private @Nullable String myPrecision;
    private @Nullable Integer myManualPosition;
    private @Nullable Integer myAutoPosition;

    private char myConversionType;

    public SubstitutionChunk(int startIndex, int endIndex) {
      super(startIndex, endIndex);
    }

    protected void setEndIndex(int endIndex) {
      myEndIndex = endIndex;
    }

    public @Nullable Integer getManualPosition() {
      return myManualPosition;
    }

    protected void setManualPosition(@Nullable Integer position) {
      myManualPosition = position;
    }

    /**
     * Automatic index of the field if neither mapping key nor explicit index was given, {@code null} otherwise.
     * <p/>
     * Basically, it's the number of automatically numbered fields preceding the current one.
     * Note that this is somewhat unreliable because it's an error to use fields with both explicit
     * and implicit indexing.
     *
     */
    public @Nullable Integer getAutoPosition() {
      return myAutoPosition;
    }

    protected void setAutoPosition(@Nullable Integer autoPosition) {
      myAutoPosition = autoPosition;
    }

    public char getConversionType() {
      return myConversionType;
    }

    public void setConversionType(char conversionType) {
      myConversionType = conversionType;
    }

    public @Nullable String getPrecision() {
      return myPrecision;
    }

    public void setPrecision(@Nullable String precision) {
      myPrecision = precision;
    }

    public @Nullable String getWidth() {
      return myWidth;
    }

    public void setWidth(@Nullable String width) {
      myWidth = width;
    }

    public @Nullable String getMappingKey() {
      return myMappingKey;
    }

    protected void setMappingKey(@Nullable String mappingKey) {
      myMappingKey = mappingKey;
    }

    /**
     * Returns either manually specified position or the one automatically assigned unless mapping key was specified instead.
     * In the latter key this method returns {@code null}.
     *
     * @see #getManualPosition()
     * @see #getAutoPosition()
     * @see #getMappingKey()
     */
    public @Nullable Integer getPosition() {
      return ObjectUtils.chooseNotNull(myManualPosition, myAutoPosition);
    }
  }

  public static class PercentSubstitutionChunk extends SubstitutionChunk {
    private @Nullable String myConversionFlags;
    private char myLengthModifier;
    private boolean myUnclosedMapping;

    public PercentSubstitutionChunk(int startIndex) {
      super(startIndex, startIndex);
    }

    public @Nullable String getConversionFlags() {
      return myConversionFlags;
    }

    private void setConversionFlags(@Nullable String conversionFlags) {
      myConversionFlags = conversionFlags;
    }

    public char getLengthModifier() {
      return myLengthModifier;
    }

    private void setLengthModifier(char lengthModifier) {
      myLengthModifier = lengthModifier;
    }

    public boolean isUnclosedMapping() {
      return myUnclosedMapping;
    }

    private void setUnclosedMapping() {
      myUnclosedMapping = true;
    }
  }

  public static class NewStyleSubstitutionChunk extends SubstitutionChunk {
    private @Nullable String myConversion;
    private @Nullable String myMappingKeyAttributeName;
    private @Nullable String myMappingKeyElementIndex;
    private boolean signOption;
    private boolean zeroPadding;
    private boolean alternateForm;
    private boolean thousandsSeparator;

    public NewStyleSubstitutionChunk(int startIndex) {
      super(startIndex, startIndex);
    }

    public @Nullable String getConversion() {
      return myConversion;
    }

    public void setConversion(@Nullable String conversion) {
      myConversion = conversion;
    }

    public boolean hasSignOption() {
      return signOption;
    }

    public void setSignOption() {
      this.signOption = true;
    }

    public boolean useAlternateForm() {
      return alternateForm;
    }

    public void setAlternateForm() {
      this.alternateForm = true;
    }

    public boolean hasZeroPadding() {
      return zeroPadding;
    }

    public void setZeroPadding() {
      this.zeroPadding = true;
    }

    public boolean hasThousandsSeparator() {
      return thousandsSeparator;
    }

    public void setThousandsSeparator() {
      this.thousandsSeparator = true;
    }

    public @Nullable String getMappingKeyAttributeName() {
      return myMappingKeyAttributeName;
    }

    public void setMappingKeyAttributeName(@NotNull String mappingKeyAttributeName) {
      myMappingKeyAttributeName = mappingKeyAttributeName;
    }

    public @Nullable String getMappingKeyElementIndex() {
      return myMappingKeyElementIndex;
    }

    public void setMappingKeyElementIndex(@Nullable String mappingKeyElementIndex) {
      myMappingKeyElementIndex = mappingKeyElementIndex;
    }
  }

  private final @NotNull String myLiteral;
  private final @NotNull List<FormatStringChunk> myResult = new ArrayList<>();
  private int myPos;
  private int mySubstitutionsCount = 0;

  // % strings
  private static final String CONVERSION_FLAGS = "#0- +";
  private static final String DIGITS = "0123456789";
  private static final String LENGTH_MODIFIERS = "hlL";
  private static final String VALID_CONVERSION_TYPES = "diouxXeEfFgGcrsba";

  // new style strings
  private static final String ALIGN_SYMBOLS = "<>=^";
  private static final String SIGN_SYMBOLS = "+- ";
  private static final String CONVERSIONS = "rsa";
  private static final char ALTERNATE_FORM_SYMBOL = '#';
  private static final char ZERO_PADDING_SYMBOL = '0';


  public PyStringFormatParser(@NotNull String literal) {
    myLiteral = literal;
  }

  public static @NotNull List<FormatStringChunk> parsePercentFormat(@NotNull String s) {
    return new PyStringFormatParser(s).parse();
  }

  public static @NotNull List<FormatStringChunk> parseNewStyleFormat(@NotNull String s) {
    return new PyStringFormatParser(s).parseNewStyle();
  }

  private @NotNull List<FormatStringChunk> parse() {
    myPos = 0;
    while (myPos < myLiteral.length()) {
      int next = myLiteral.indexOf('%', myPos);
      while(next >= 0 && next < myLiteral.length()-1 && myLiteral.charAt(next+1) == '%') {
        next = myLiteral.indexOf('%', next+2);
      }
      if (next < 0) break;
      if (next > myPos) {
        myResult.add(new ConstantChunk(myPos, next));
      }
      myPos = next;
      parseSubstitution();
    }
    if (myPos < myLiteral.length()) {
      myResult.add(new ConstantChunk(myPos, myLiteral.length()));
    }
    return myResult;
  }

  public List<FormatStringChunk> parseNewStyle() {
    final List<FormatStringChunk> results = new ArrayList<>();
    final Matcher matcher = NEW_STYLE_FORMAT_TOKENS.matcher(myLiteral);
    int autoPositionedFieldsCount = 0;
    boolean skipNext = false;
    while (matcher.find()) {
      final String group = matcher.group();
      myPos = matcher.start();
      final int end = matcher.end();
      if (group.endsWith("\\N")) {
        skipNext = true;
        continue;
      }
      if ("{{".equals(group) || "}}".equals(group)) {
        results.add(new ConstantChunk(myPos, end));
      }
      else if (group.startsWith("{") && group.endsWith("}")) {
        if (!skipNext) {
          autoPositionedFieldsCount = parseNewStyleSubstitution(results, end, autoPositionedFieldsCount);
        }
        skipNext = false;
      }
      else {
        results.add(new ConstantChunk(myPos, end));
      }
    }
    return results;
  }

  private int parseNewStyleSubstitution(List<FormatStringChunk> results, int end, int autoPositionedFieldsCount) {
    final NewStyleSubstitutionChunk chunk = new NewStyleSubstitutionChunk(myPos);
    chunk.setEndIndex(end);

    // skip "{"
    myPos++;
    // name
    final int nameEnd = StringUtil.indexOfAny(myLiteral, "!:.[}", myPos, end);
    if (nameEnd > 0 && myPos < nameEnd) {
      final String name = myLiteral.substring(myPos, nameEnd);
      try {
        final int number = Integer.parseInt(name);
        chunk.setManualPosition(number);
      }
      catch (NumberFormatException e) {
        chunk.setMappingKey(name);
      }
      myPos = nameEnd;
    }
    else {
      chunk.setAutoPosition(autoPositionedFieldsCount);
      autoPositionedFieldsCount++;
    }

    // parse field name attribute name
    if (isAt('.')) {
      myPos++;

      final int attributeEnd = StringUtil.indexOfAny(myLiteral, "!:.[}", myPos, end);
      if (attributeEnd > 0 && myPos < attributeEnd) {
        final String attributeName = myLiteral.substring(myPos, attributeEnd);
        chunk.setMappingKeyAttributeName(attributeName);
        myPos = attributeEnd;
      }
    }

    // parse field name element indexes
    if (isAt('[')) {
      myPos++;

      final int indexElementEnd = StringUtil.indexOfAny(myLiteral, "!:.]", myPos, end);
      if (indexElementEnd > 0 && myPos < indexElementEnd) {
        final String index = myLiteral.substring(myPos, indexElementEnd);
        chunk.setMappingKeyElementIndex(index);
        myPos = indexElementEnd + 1;
      }
    }

    // skip other attribute names and element indexes
    while (isAt('.') || isAt('[')) {
      if (isAt('.')) {
        myPos++;
        final int attributeEnd = StringUtil.indexOfAny(myLiteral, "!:.[", myPos, end);
        if (attributeEnd > 0 && myPos < attributeEnd) {
          myPos = attributeEnd;
        }
      }
      else {
        myPos++;
        final int attributeEnd = StringUtil.indexOf(myLiteral, ']', myPos, end);
        if (attributeEnd > 0 && myPos < attributeEnd) {
          myPos = attributeEnd + 1;
        }
      }
    }

    // conversion
    myPos = Math.max(myPos, StringUtil.indexOf(myLiteral, '!', myPos, end) + 1);
    final int conversionEnd = StringUtil.indexOfAny(myLiteral, ":}", myPos, end);
    if (conversionEnd - myPos == 1) {
      final String conversion = myLiteral.substring(myPos, conversionEnd);
      if (StringUtil.containsAnyChar(conversion, CONVERSIONS)) {
        chunk.setConversion(conversion);
        myPos = conversionEnd;
      }
    }

    // parse format spec
    // [[fill]align][sign][#][0][width][,][.precision][type]
    if (isAt(':')) {
      //skip ":"
      myPos++;

      //skip align options
      myPos = Math.max(myPos, StringUtil.indexOfAny(myLiteral, ALIGN_SYMBOLS, myPos, end) + 1);

      if (isAtSet(SIGN_SYMBOLS)) {
        chunk.setSignOption();
        myPos++;
      }

      if (isAt(ALTERNATE_FORM_SYMBOL)) {
        chunk.setAlternateForm();
        myPos++;
      }

      if (isAt(ZERO_PADDING_SYMBOL)) {
        chunk.setZeroPadding();
        myPos++;
      }

      chunk.setWidth(parseWhileCharacterInSet(DIGITS));

      if (isAt(',')) {
        myPos++;
        chunk.setThousandsSeparator();
      }

      if (isAt('.')) {
        myPos++;
        chunk.setPrecision(parseWhileCharacterInSet(DIGITS));
      }

      if (myPos < end - 1) {
        chunk.setConversionType(myLiteral.charAt(myPos));
      }
    }


    results.add(chunk);
    return autoPositionedFieldsCount;
  }

  private void parseSubstitution() {
    assert myLiteral.charAt(myPos) == '%';
    PercentSubstitutionChunk chunk = new PercentSubstitutionChunk(myPos);
    myResult.add(chunk);
    myPos++;
    if (isAt('(')) {
      int mappingEnd = myLiteral.indexOf(')', myPos + 1);
      if (mappingEnd < 0) {
        chunk.setEndIndex(myLiteral.length());
        chunk.setMappingKey(myLiteral.substring(myPos + 1));
        chunk.setUnclosedMapping();
        myPos = myLiteral.length();
        return;
      }
      chunk.setMappingKey(myLiteral.substring(myPos + 1, mappingEnd));
      myPos = mappingEnd + 1;
    }
    else  {
      chunk.setAutoPosition(mySubstitutionsCount);
      mySubstitutionsCount++;
    }
    chunk.setConversionFlags(parseWhileCharacterInSet(CONVERSION_FLAGS));
    chunk.setWidth(parseWidth());
    if (isAt('.')) {
      myPos++;
      chunk.setPrecision(parseWidth());
    }
    if (isAtSet(LENGTH_MODIFIERS)) {
      chunk.setLengthModifier(myLiteral.charAt(myPos));
      myPos++;
    }
    if (isAtSet(VALID_CONVERSION_TYPES)) {
      chunk.setConversionType(myLiteral.charAt(myPos));
      myPos++;
    }
    chunk.setEndIndex(myPos);
  }

  private boolean isAtSet(final @NotNull String characterSet) {
    return myPos < myLiteral.length() && characterSet.indexOf(myLiteral.charAt(myPos)) >= 0;
  }

  private boolean isAt(final char c) {
    return myPos < myLiteral.length() && myLiteral.charAt(myPos) == c;
  }

  private @NotNull String parseWidth() {
    if (isAt('*')) {
      myPos++;
      return "*";
    }
    return parseWhileCharacterInSet(DIGITS);
  }

  private @NotNull String parseWhileCharacterInSet(final @NotNull String characterSet) {
    int flagStart = myPos;
    while (isAtSet(characterSet)) {
      myPos++;
    }
    return myLiteral.substring(flagStart, myPos);
  }

  public static @NotNull List<SubstitutionChunk> filterSubstitutions(@NotNull List<? extends FormatStringChunk> chunks) {
    final List<SubstitutionChunk> results = new ArrayList<>();
    for (FormatStringChunk chunk : chunks) {
      if (chunk instanceof SubstitutionChunk) {
        results.add((SubstitutionChunk)chunk);
      }
    }
    return results;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static @NotNull List<SubstitutionChunk> getPositionalSubstitutions(@NotNull List<? extends SubstitutionChunk> substitutions) {
    final ArrayList<SubstitutionChunk> result = new ArrayList<>();
    for (SubstitutionChunk s : substitutions) {
      if (s.getMappingKey() == null) {
        result.add(s);
      }
    }
    return result;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static @NotNull Map<String, SubstitutionChunk> getKeywordSubstitutions(@NotNull List<? extends SubstitutionChunk> substitutions) {
    final Map<String, SubstitutionChunk> result = new HashMap<>();
    for (SubstitutionChunk s : substitutions) {
      final String key = s.getMappingKey();
      if (key != null) {
        result.put(key, s);
      }
    }
    return result;
  }

  public static @NotNull List<TextRange> substitutionsToRanges(@NotNull List<? extends SubstitutionChunk> substitutions) {
    final List<TextRange> ranges = new ArrayList<>();
    for (SubstitutionChunk substitution : substitutions) {
      ranges.add(TextRange.create(substitution.getStartIndex(), substitution.getEndIndex()));
    }
    return ranges;
  }

  /**
   * Return the RHS operand of %-based string literal format expression.
   */
  public static @Nullable PyExpression getFormatValueExpression(@NotNull PyStringLiteralExpression element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PyBinaryExpression binaryExpr) {
      if (binaryExpr.isOperator("%")) {
        PyExpression expr = binaryExpr.getRightExpression();
        while (expr instanceof PyParenthesizedExpression) {
          expr = ((PyParenthesizedExpression)expr).getContainedExpression();
        }
        return expr;
      }
    }
    return null;
  }

  /**
   * Return the argument list of the str.format() literal format expression.
   */
  public static @Nullable PyArgumentList getNewStyleFormatValueExpression(@NotNull PyStringLiteralExpression element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PyQualifiedExpression qualifiedExpr) {
      final String name = qualifiedExpr.getReferencedName();
      if (PyNames.FORMAT.equals(name)) {
        final PsiElement parent2 = qualifiedExpr.getParent();
        if (parent2 instanceof PyCallExpression callExpr) {
          return callExpr.getArgumentList();
        }
      }
    }
    return null;
  }

  public static @NotNull List<TextRange> getEscapeRanges(@NotNull String s) {
    final List<TextRange> ranges = new ArrayList<>();
    Matcher matcher = PyStringLiteralDecoder.PATTERN_ESCAPE.matcher(s);
    while (matcher.find()) {
      ranges.add(TextRange.create(matcher.start(), matcher.end()));
    }
    return ranges;
  }
}
