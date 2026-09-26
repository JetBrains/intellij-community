// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLAnchor;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.Set;
import java.util.regex.Pattern;

public final class YamlGenericValueAdapter implements JsonValueAdapter {
  private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?");
  private static final Pattern INTEGER_PATTERN = Pattern.compile("[-+]?(?:0[bB][0-1_]+|0[oO][0-7_]+|0[xX][0-9a-fA-F_]+|[0-9][0-9_]*)");

  private static final @NotNull Set<String> NULLS = Set.of("null", "Null", "NULL", "~");
  private static final @NotNull Set<String> BOOLS = Set.of("true", "True", "TRUE", "false", "False", "FALSE");
  private static final @NotNull Set<String> INFS = Set.of(".inf", ".Inf", ".INF");
  private static final @NotNull Set<String> NANS = Set.of(".nan", ".NaN", ".NAN");
  private final @NotNull YAMLValue myValue;

  public YamlGenericValueAdapter(@NotNull YAMLValue value) {myValue = value;}

  @Override
  public boolean isShouldBeIgnored() {
    return true;
  }

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public boolean isStringLiteral() {
    return !isNumberLiteral() && !isBooleanLiteral() && !isNull();
  }

  private String getTextWithoutRefs() {
    YAMLAnchor[] anchors = PsiTreeUtil.getChildrenOfType(myValue, YAMLAnchor.class);
    if (anchors == null || anchors.length == 0) return myValue.getText();
    int endOffset = anchors[anchors.length - 1].getTextRange().getEndOffset();
    TextRange valueTextRange = myValue.getTextRange();
    int offset = valueTextRange.getEndOffset();
    TextRange range = new TextRange(endOffset, offset);
    range = range.shiftLeft(valueTextRange.getStartOffset());
    String text = myValue.getText();
    return text.substring(range.getStartOffset()).trim();
  }

  private static boolean hasTag(@NotNull String text, @NotNull String tagName) {
    return StringUtil.startsWith(text, "!!" + tagName);
  }

  @Override
  public boolean isNumberLiteral() {
    String text = getTextWithoutRefs();
    return isNumber(text);
  }

  @Override
  public boolean isBooleanLiteral() {
    String text = getTextWithoutRefs();
    return BOOLS.contains(text) || hasTag(text, "bool");
  }

  @Override
  public boolean isNull() {
    String text = getTextWithoutRefs();
    return NULLS.contains(text) || hasTag(text, "null");
  }

  @Override
  public @NotNull PsiElement getDelegate() {
    return myValue;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getAsObject() {
    return null;
  }

  @Override
  public @Nullable JsonArrayValueAdapter getAsArray() {
    return null;
  }

  @Override
  public boolean shouldCheckIntegralRequirements() {
    return false;
  }

  @Override
  public boolean shouldCheckAsValue() {
    return !isNonFinite(getTextWithoutRefs());
  }

  private static boolean isNumber(@Nullable String s) {
    if (s == null) return false;
    return isInteger(s) || isFloat(s);
  }

  // http://yaml.org/spec/1.2/spec.html#id2803828
  private static boolean isInteger(@NotNull String s) {
    if (s.isEmpty()) return false;
    if ("0".equals(s) || "-0".equals(s) || "+0".equals(s)) return true;
    if (hasTag(s, "int")) return true;
    if (matchesInt(s)) return true;
    return false;
  }

  private static boolean matchesInt(@NotNull String s) {
    return INTEGER_PATTERN.matcher(s).matches();
  }

  // http://yaml.org/spec/1.2/spec.html#id2804092
  private static boolean isFloat(@NotNull String s) {
    if (INFS.contains(trimSign(s)) || NANS.contains(trimSign(s))) return true;
    if (hasTag(s, "float")) return true;
    return FLOAT_PATTERN.matcher(s).matches();
  }

  private static boolean isNonFinite(@NotNull String s) {
    if (hasTag(s, "float")) {
      int spaceIndex = s.indexOf(' ');
      s = spaceIndex > 0 ? s.substring(spaceIndex + 1).trim() : "";
    }
    String value = trimSign(s);
    return INFS.contains(value) || NANS.contains(value);
  }

  private static @NotNull String trimSign(@NotNull String s) {
    if (s.isEmpty()) return s;
    char c = s.charAt(0);
    return c == '+' || c == '-' ? s.substring(1) : s;
  }
}
