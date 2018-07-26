// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.schema;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLValue;

import java.util.regex.Pattern;

public class YamlGenericValueAdapter implements JsonValueAdapter {
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
    String text = myValue.getText();
    return !hasNonStringTags(text); /*values should always validate as string*/
  }

  private static boolean hasNonStringTags(@NotNull String text) {
    return hasTag(text, "bool")
      || hasTag(text, "null")
      || hasTag(text, "int")
      || hasTag(text, "float");
  }

  private static boolean hasTag(@NotNull String text, @NotNull String tagName) {
    return StringUtil.startsWith(text, "!!" + tagName);
  }

  @Override
  public boolean isNumberLiteral() {
    String text = myValue.getText();
    return isNumber(text);
  }

  @Override
  public boolean isBooleanLiteral() {
    String text = myValue.getText();
    return "true".equals(text) || "false".equals(text) || hasTag(text, "bool");
  }

  @Override
  public boolean isNull() {
    String text = myValue.getText();
    return "null".equals(text) || hasTag(text, "null");
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
    if ("0".equals(s)) return true;
    if (hasTag(s, "int")) return true;
    int startIndex = s.charAt(0) == '-' ? 1 : 0;
    for (int i = startIndex; i < s.length(); ++i) {
      if (i == startIndex && s.charAt(i) == '0') return false;
      if (!Character.isDigit(s.charAt(i))) return false;
    }
    return true;
  }

  // http://yaml.org/spec/1.2/spec.html#id2804092
  private static boolean isFloat(@NotNull String s) {
    if (".inf".equals(s) || "-.inf".equals(s) || ".nan".equals(s)) return true;
    if (hasTag(s, "float")) return true;
    return Pattern.matches("-?[1-9](\\.[0-9]*[1-9])?(e[-+][1-9][0-9]*)?", s);
  }
}
