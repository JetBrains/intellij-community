package org.jetbrains.plugins.textmate.language.syntax;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

/**
 * Syntax rule of languages from TextMate bundle.
 * Consists of:
 * <ul>
 * <li>
 * String attributes - string attributes of syntax node {@link org.jetbrains.plugins.textmate.Constants#STRING_KEY_NAMES}
 * </li>
 * <li>
 * Regex attributes - regex attributes of syntax node {@link org.jetbrains.plugins.textmate.Constants#REGEX_KEY_NAMES}
 * </li>
 * <li>
 * Dict attributes - plist attributes of syntax node {@link org.jetbrains.plugins.textmate.Constants#DICT_KEY_NAMES}
 * </li>
 * <li>
 * Repository - set of named syntax rules (nodes) which can be included from other places in the grammar.
 * </li>
 * <li>
 * Children rules - set of nested syntax rules (from 'patterns' node)
 * </li>
 * </ul>
 * <p/>
 * User: zolotov
 */
public interface MutableSyntaxNodeDescriptor extends SyntaxNodeDescriptor {
  void addChild(SyntaxNodeDescriptor descriptor);

  void addInjection(@NotNull InjectionNodeDescriptor injection);

  void setStringAttribute(String key, String value);

  void setPlistAttribute(String key, Plist value);

  void setRegexAttribute(String key, RegexFacade value);

  void appendRepository(String key, SyntaxNodeDescriptor descriptor);

  void setScopeName(@NotNull String scopeName);
}
