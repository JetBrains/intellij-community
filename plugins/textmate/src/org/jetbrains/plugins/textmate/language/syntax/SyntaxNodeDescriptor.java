package org.jetbrains.plugins.textmate.language.syntax;

import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

import java.util.List;

/**
 * Syntax rule of languages from TextMate bundle.
 * Consists of:
 * <ul>
 * <li>
 * String attributes - string attributes of syntax node {@link Constants.StringKey}
 * </li>
 * <li>
 * Regex attributes - regex attributes of syntax node {@link Constants.RegexKey}
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
public interface SyntaxNodeDescriptor {
  SyntaxNodeDescriptor EMPTY_NODE = new SyntaxNodeDescriptorImpl(null);

  @Nullable
  String getStringAttribute(@NotNull Constants.StringKey key);

  @Nullable
  TIntObjectHashMap<String> getCaptures(@NotNull Constants.CaptureKey key);

  @Nullable
  RegexFacade getRegexAttribute(@NotNull Constants.RegexKey key);

  @NotNull
  List<SyntaxNodeDescriptor> getChildren();

  @NotNull
  List<InjectionNodeDescriptor> getInjections();

  @NotNull
  SyntaxNodeDescriptor findInRepository(int ruleId);

  /**
   * @return scope name if node is root for language or empty string otherwise
   */
  @NotNull
  String getScopeName();

  @Nullable
  SyntaxNodeDescriptor getParentNode();
}
