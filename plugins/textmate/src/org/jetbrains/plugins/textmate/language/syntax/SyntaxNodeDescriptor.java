package org.jetbrains.plugins.textmate.language.syntax;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

import java.util.List;

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
public interface SyntaxNodeDescriptor {
  SyntaxNodeDescriptor EMPTY_NODE = new SyntaxNodeDescriptorImpl(null);

  @Nullable
  String getStringAttribute(String key);

  @Nullable
  Plist getPlistAttribute(String key);

  @Nullable
  RegexFacade getRegexAttribute(String key);

  @NotNull
  List<SyntaxNodeDescriptor> getChildren();

  @NotNull
  List<InjectionNodeDescriptor> getInjections();

  @NotNull
  SyntaxNodeDescriptor findInRepository(String key);

  /**
   * @return scope name if node is root for language or empty string otherwise
   */
  @NotNull
  String getScopeName();

  @Nullable
  SyntaxNodeDescriptor getParentNode();
}
