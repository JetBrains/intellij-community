// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.jetbrains.python.psi.LanguageLevel;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.UnicodeCharacterNames;
import org.intellij.lang.regexp.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public final class PythonRegExpHost implements RegExpLanguageHost {
  private final DefaultRegExpPropertiesProvider myPropertiesProvider;

  public PythonRegExpHost() {
    myPropertiesProvider = DefaultRegExpPropertiesProvider.getInstance();
  }

  @Override
  public boolean characterNeedsEscaping(char c, boolean isInClass) {
    return c == '\"' || c == '\'';
  }

  @Override
  public boolean supportsPerl5EmbeddedComments() {
    return true;
  }

  @Override
  public boolean supportsPossessiveQuantifiers() {
    return false;
  }

  @Override
  public boolean supportsPythonConditionalRefs() {
    return true;
  }

  @Override
  public boolean supportConditionalCondition(RegExpAtom condition) {
    if (condition instanceof RegExpGroup) {
      return false;
    }
    return condition.getNode().getFirstChildNode().getElementType() == RegExpTT.GROUP_BEGIN;
  }

  @Override
  public boolean supportsNamedGroupSyntax(RegExpGroup group) {
    return group.getType() == RegExpGroup.Type.PYTHON_NAMED_GROUP;
  }

  @Override
  public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
    return ref.isPythonNamedGroupRef();
  }

  @NotNull
  @Override
  public EnumSet<RegExpGroup.Type> getSupportedNamedGroupTypes(RegExpElement context) {
    return EnumSet.of(RegExpGroup.Type.PYTHON_NAMED_GROUP);
  }

  @Override
  public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
    return false;
  }

  @Override
  public Lookbehind supportsLookbehind(@NotNull RegExpGroup lookbehindGroup) {
    return Lookbehind.FIXED_LENGTH_ALTERNATION;
  }

  @Override
  public Long getQuantifierValue(@NotNull RegExpNumber number) {
    try {
      final long result = Long.parseLong(number.getUnescapedText());
      if (result >= 0xFFFFFFFFL /* max unsigned int 32 bits */) return null;
      return result;
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public boolean isValidCategory(@NotNull String category) {
    return myPropertiesProvider.isValidCategory(category);
  }

  @Override
  public String[] @NotNull [] getAllKnownProperties() {
    return myPropertiesProvider.getAllKnownProperties();
  }

  @Nullable
  @Override
  public String getPropertyDescription(@Nullable String name) {
    return myPropertiesProvider.getPropertyDescription(name);
  }

  @Override
  public String[] @NotNull [] getKnownCharacterClasses() {
    return myPropertiesProvider.getKnownCharacterClasses();
  }

  @Override
  public boolean supportsNamedCharacters(RegExpNamedCharacter namedCharacter) {
    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(namedCharacter.getProject()).getInjectionHost(namedCharacter);
    return host == null || LanguageLevel.forElement(host).isAtLeast(LanguageLevel.PYTHON38);
  }

  @Override
  public boolean isValidNamedCharacter(RegExpNamedCharacter namedCharacter) {
    return UnicodeCharacterNames.getCodePoint(namedCharacter.getName()) >= 0;
  }
}
