// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * User : ktisha
 */
public final class PyNameSuggestionProvider implements NameSuggestionProvider {
  @Override
  public SuggestedNameInfo getSuggestedNames(PsiElement element, PsiElement nameSuggestionContext, Set<String> result) {
    if (!(element instanceof PyElement)) return null;
    final String name = ((PyElement)element).getName();
    if (name == null) return null;

    if (element instanceof PyClass) {
      result.add(toCamelCase(name, true));
    }
    else if (element instanceof PyFunction || element instanceof PyParameter) {
      result.add(toUnderscores(name));
    }
    else {
      result.add(toUnderscores(name));
      final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
      if (assignmentStatement != null) return null;
      result.add(StringUtil.toUpperCase(name));
      result.add(toCamelCase(name, false));
    }
    return SuggestedNameInfo.NULL_INFO;
  }

  public static @NotNull String toUnderscores(final @NotNull String name) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      final char prev = i > 0 ? name.charAt(i - 1) : '\0';
      final char c = name.charAt(i);
      final char next = i + 1 < name.length() ? name.charAt(i + 1) : '\0';
      if (Character.isUpperCase(c)) {
        if (Character.isLowerCase(prev) || Character.isDigit(prev) || Character.isUpperCase(prev) && Character.isLowerCase(next)) {
          result.append("_");
        }
        result.append(Character.toLowerCase(c));
      }
      else {
        result.append(c);
      }
    }
    return result.toString();
  }

  private static @NotNull String toCamelCase(final @NotNull String name, boolean uppercaseFirstLetter) {
    final List<String> strings = StringUtil.split(name, "_");
    if (!strings.isEmpty()) {
      final StringBuilder buf = new StringBuilder();
      String str = StringUtil.toLowerCase(strings.get(0));
      if (uppercaseFirstLetter) str = StringUtil.capitalize(str);
      buf.append(str);
      for (int i = 1; i < strings.size(); i++) {
        buf.append(StringUtil.capitalize(StringUtil.toLowerCase(strings.get(i))));
      }
      return buf.toString();
    }
    return name;
  }
}
