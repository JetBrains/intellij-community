// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull private static final Set<String> NULLS = Set.of("null", "Null", "NULL", "~");
  @NotNull private static final Set<String> BOOLS = Set.of("true", "True", "TRUE", "false", "False", "FALSE");
  @NotNull private static final Set<String> INFS = Set.of(".inf", ".Inf", ".INF");
  @NotNull private static final Set<String> NANS = Set.of(".nan", ".NaN", ".NAN");
  @NotNull private final YAMLValue myValue;

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

  @NotNull
  @Override
  public PsiElement getDelegate() {
    return myValue;
  }

  @Nullable
  @Override
  public JsonObjectValueAdapter getAsObject() {
    return null;
  }

  @Nullable
  @Override
  public JsonArrayValueAdapter getAsArray() {
    return null;
  }

  @Override
  public boolean shouldCheckIntegralRequirements() {
    return false;
  }

  private static boolean isNumber(@Nullable String s) {
    if (s == null) return false;
    return isInteger(s) || isFloat(s);
  }

  // http://yaml.org/spec/1.2/spec.html#id2803828
  private static boolean isInteger(@NotNull String s) {
    if (s.length() == 0) return false;
    if ("0".equals(s) || "-0".equals(s) || "+0".equals(s)) return true;
    if (hasTag(s, "int")) return true;
    if (matchesInt(s)) return true;
    return false;
  }

  private static boolean matchesInt(@NotNull String s) {
    char charZero = s.charAt(0);
    int startIndex = (charZero == '-' || charZero == '+') ? 1 : 0;
    char baseSign = ' ';
    boolean expectBase = false;
    for (int i = startIndex; i < s.length(); ++i) {
      if (i == startIndex && s.charAt(i) == '0') {
        if (startIndex != 0) return false;
        expectBase = true;
        continue;
      }
      if (i == startIndex + 1 && expectBase) {
        char c = s.charAt(i);
        if (c != 'o' && c != 'x') return false;
        baseSign = c;
      }

      if (baseSign == ' ' && !Character.isDigit(s.charAt(i))) return false;
      else if (baseSign == 'o' && !StringUtil.isOctalDigit(s.charAt(i))) return false;
      else if (baseSign == 'x' && !StringUtil.isHexDigit(s.charAt(i))) return false;
    }
    return true;
  }

  // http://yaml.org/spec/1.2/spec.html#id2804092
  private static boolean isFloat(@NotNull String s) {
    if (INFS.contains(trimSign(s)) || NANS.contains(s)) return true;
    if (hasTag(s, "float")) return true;
    return Pattern.matches("[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?", s);
  }

  @NotNull
  private static String trimSign(@NotNull String s) {
    if (s.isEmpty()) return s;
    char c = s.charAt(0);
    return c == '+' || c == '-' ? s.substring(1) : s;
  }
}
