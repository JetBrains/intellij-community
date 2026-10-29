// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.LanguageLevel;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.UnicodeCharacterNames;
import org.intellij.lang.regexp.psi.RegExpAtom;
import org.intellij.lang.regexp.psi.RegExpBoundary;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedCharacter;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;
import org.intellij.lang.regexp.psi.RegExpNumber;
import org.intellij.lang.regexp.psi.RegExpSimpleClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

import static com.jetbrains.python.codeInsight.regexp.PythonMrabRegexpKt.isMrabRegexp;

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
  public boolean supportsPossessiveQuantifiers(RegExpElement context) {
    if (isMrabRegexp(context)) {
      return true;
    }
    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(context.getProject()).getInjectionHost(context);
    return host != null && LanguageLevel.forElement(host).isAtLeast(LanguageLevel.PYTHON311);
  }

  @Override
  public boolean supportsPythonConditionalRefs() {
    return true;
  }

  @Override
  public boolean supportConditionalCondition(RegExpAtom condition) {
    if (condition instanceof RegExpGroup) {
      return isMrabRegexp(condition) && condition.getNode().findChildByType(RegExpTT.PCRE_VERSION) == null;
    }
    return condition.getNode().getFirstChildNode().getElementType() == RegExpTT.GROUP_BEGIN;
  }

  @Override
  public boolean supportsNamedGroupSyntax(RegExpGroup group) {
    return getSupportedNamedGroupTypes(group).contains(group.getType());
  }

  @Override
  public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
    if (ref.isPythonNamedGroupRef()) {
      return true;
    }
    if (!isMrabRegexp(ref)) {
      return false;
    }
    final IElementType type = ref.getNode().getFirstChildNode().getElementType();
    return type == RegExpTT.RUBY_NAMED_GROUP_CALL ||
           type == RegExpTT.RUBY_QUOTED_NAMED_GROUP_CALL ||
           type == RegExpTT.PCRE_RECURSIVE_NAMED_GROUP_REF;
  }

  @Override
  public @NotNull EnumSet<RegExpGroup.Type> getSupportedNamedGroupTypes(RegExpElement context) {
    return isMrabRegexp(context)
           ? EnumSet.of(RegExpGroup.Type.PYTHON_NAMED_GROUP, RegExpGroup.Type.NAMED_GROUP)
           : EnumSet.of(RegExpGroup.Type.PYTHON_NAMED_GROUP);
  }

  @Override
  public boolean isDuplicateGroupNamesAllowed(@NotNull RegExpGroup group) {
    return isMrabRegexp(group);
  }

  @Override
  public boolean supportsSimpleClass(RegExpSimpleClass simpleClass) {
    return switch (simpleClass.getKind()) {
      case NON_HORIZONTAL_SPACE, NON_VERTICAL_SPACE -> !isMrabRegexp(simpleClass);
      case ANY, DIGIT, NON_DIGIT, WORD, NON_WORD, SPACE, NON_SPACE, HORIZONTAL_SPACE, VERTICAL_SPACE,
           XML_NAME_START, NON_XML_NAME_START, XML_NAME_PART, NON_XML_NAME_PART, UNICODE_GRAPHEME, UNICODE_LINEBREAK -> true;
    };
  }

  @Override
  public boolean supportsBoundary(RegExpBoundary boundary) {
    return switch (boundary.getType()) {
      case RESET_MATCH -> isMrabRegexp(boundary);
      case LINE_START, LINE_END, WORD, UNICODE_EXTENDED_GRAPHEME, NON_WORD, BEGIN, END, END_NO_LINE_TERM, PREVIOUS_MATCH ->
        RegExpLanguageHost.super.supportsBoundary(boundary);
    };
  }

  @Override
  public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
    return false;
  }

  @Override
  public Lookbehind supportsLookbehind(@NotNull RegExpGroup lookbehindGroup) {
    return isMrabRegexp(lookbehindGroup) ? Lookbehind.FULL : Lookbehind.FIXED_LENGTH_ALTERNATION;
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

  /**
   * The {@code regex} module accepts many more property names than the default provider knows, for example {@code \p{Latin}} or
   * {@code \p{Posix_Alnum}}. The names also follow the Unicode version of the installed module, so this method accepts every name.
   */
  @Override
  public boolean isValidCategory(@NotNull String category, @NotNull PsiElement context) {
    return isMrabRegexp(context) || myPropertiesProvider.isValidCategory(category);
  }

  @Override
  public String[] @NotNull [] getAllKnownProperties() {
    return myPropertiesProvider.getAllKnownProperties();
  }

  @Override
  public @Nullable String getPropertyDescription(@Nullable String name) {
    return myPropertiesProvider.getPropertyDescription(name);
  }

  @Override
  public String[] @NotNull [] getKnownCharacterClasses() {
    return myPropertiesProvider.getKnownCharacterClasses();
  }

  @Override
  public boolean supportsNamedCharacters(RegExpNamedCharacter namedCharacter) {
    if (isMrabRegexp(namedCharacter)) {
      return true;
    }
    PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(namedCharacter.getProject()).getInjectionHost(namedCharacter);
    return host == null || LanguageLevel.forElement(host).isAtLeast(LanguageLevel.PYTHON38);
  }

  @Override
  public boolean isValidNamedCharacter(RegExpNamedCharacter namedCharacter) {
    return UnicodeCharacterNames.getCodePoint(namedCharacter.getName()) >= 0;
  }

  @Override
  public boolean isValidGroupName(String name, @NotNull RegExpGroup group) {
    // non-ascii characters are allowed as group names in Python 3
    // the specification is `<XID_Start> <XID_Continue>*`
    int offset = 0;
    int codePoint = name.codePointAt(offset);
    
    // First character must be XID_Start
    if (!Character.isUnicodeIdentifierStart(codePoint)) {
      return false;
    }
    
    offset += Character.charCount(codePoint);
    
    // Remaining characters must be XID_Continue
    while (offset < name.length()) {
      codePoint = name.codePointAt(offset);
      if (!Character.isUnicodeIdentifierPart(codePoint)) {
        return false;
      }
      offset += Character.charCount(codePoint);
    }
    
    return true;
  }
}
