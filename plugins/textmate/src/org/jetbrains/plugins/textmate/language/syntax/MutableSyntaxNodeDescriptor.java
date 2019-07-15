package org.jetbrains.plugins.textmate.language.syntax;

import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

/**
 * Syntax rule of languages from TextMate bundle.
 * Consists of:
 * <ul>
 * <li>
 * String attributes - string attributes of syntax node {@link Constants.StringKey}
 * </li>
 * <li>
 * Regex attributes - regex attributes of syntax node {@link Constants#REGEX_KEY_NAMES}
 * </li>
 * <li>
 * Captures attributes - captures attributes of syntax node {@link Constants.CaptureKey}
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

  void setStringAttribute(@NotNull Constants.StringKey key, String value);

  void setCaptures(@NotNull Constants.CaptureKey key, @Nullable TIntObjectHashMap<String> captures);

  void setRegexAttribute(String key, RegexFacade value);

  void appendRepository(int ruleId, SyntaxNodeDescriptor descriptor);

  void setScopeName(@NotNull String scopeName);

  void compact();
}
